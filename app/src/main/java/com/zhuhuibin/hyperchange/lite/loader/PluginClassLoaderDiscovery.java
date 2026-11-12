// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.loader;

import java.util.IdentityHashMap;

import io.github.libxposed.api.XposedInterface;

/** The global class-loading callback deliberately has no Kotlin dependency. */
public final class PluginClassLoaderDiscovery implements XposedInterface.Hooker {
    public interface Scheduler {
        /** Enqueue for later execution; implementations must never run the task inline. */
        boolean post(Runnable task);
    }

    public interface Listener {
        void onDiscovered(ClassLoader loader) throws Throwable;
        void onFailure(Throwable error);
    }

    private static final int PENDING = 1;
    private static final int INSTALLED = 2;
    private static final int FAILED = 3;
    private final ThreadLocal<Boolean> active = new ThreadLocal<>();
    private final IdentityHashMap<ClassLoader, Integer> states = new IdentityHashMap<>();
    private final Scheduler scheduler;
    private final Listener listener;

    public PluginClassLoaderDiscovery(Scheduler scheduler, Listener listener) {
        this.scheduler = scheduler;
        this.listener = listener;
    }

    @Override
    public Object intercept(XposedInterface.Chain chain) throws Throwable {
        // Both loadClass overloads share this instance. Nested calls leave the outer
        // guard untouched and preserve the original result/exception exactly once.
        if (active.get() == Boolean.TRUE) return chain.proceed();
        active.set(Boolean.TRUE);
        try {
            Object loaded = chain.proceed();
            try {
                Object argument = chain.getArg(0);
                if (argument instanceof String && loaded instanceof Class<?>) {
                    String name = (String) argument;
                    if (name.startsWith("miui.systemui.controlcenter.") ||
                            name.startsWith("com.android.systemui.miui.volume.")) {
                        ClassLoader loader = ((Class<?>) loaded).getClassLoader();
                        if (loader != null) enqueue(loader);
                    }
                }
            } catch (Throwable ignored) {
                // Discovery must not turn a successful native class load into failure.
                // Do not initialize a logger or Kotlin error helper in this callback.
            }
            return loaded;
        } finally {
            active.remove();
        }
    }

    private void enqueue(ClassLoader loader) {
        synchronized (states) {
            if (states.containsKey(loader)) return;
            states.put(loader, PENDING);
        }
        boolean accepted = false;
        try {
            accepted = scheduler.post(new Delivery(loader));
        } finally {
            if (!accepted) {
                synchronized (states) {
                    Integer state = states.get(loader);
                    if (state != null && state == PENDING) states.remove(loader);
                }
            }
        }
    }

    private final class Delivery implements Runnable {
        private final ClassLoader loader;

        private Delivery(ClassLoader loader) { this.loader = loader; }

        @Override
        public void run() {
            // Fail closed if an incorrect scheduler tries to install inside loadClass.
            if (active.get() == Boolean.TRUE) {
                synchronized (states) { states.remove(loader); }
                return;
            }
            try {
                listener.onDiscovered(loader);
                synchronized (states) { states.put(loader, INSTALLED); }
            } catch (Throwable error) {
                // A failed adapter is terminal for this loader. Repeated class loads
                // must not create an installation/error storm during startup.
                synchronized (states) { states.put(loader, FAILED); }
                try { listener.onFailure(error); } catch (Throwable ignored) { }
            }
        }
    }
}
