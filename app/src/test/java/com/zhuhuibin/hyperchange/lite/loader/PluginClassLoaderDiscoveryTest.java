package com.zhuhuibin.hyperchange.lite.loader;

import io.github.libxposed.api.XposedInterface;
import org.junit.Test;

import java.lang.reflect.Executable;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class PluginClassLoaderDiscoveryTest {
    private static final String TARGET = "miui.systemui.controlcenter.Fixture";

    @Test public void nestedOverloadsOnlyProceedAndInstallAfterCallbackReturns() throws Throwable {
        Queue queue = new Queue();
        AtomicInteger installed = new AtomicInteger();
        PluginClassLoaderDiscovery hook = hook(queue, installed, new AtomicInteger());
        Chain nested = new Chain("com.android.systemui.miui.volume.Fixture", () -> Fixture.class);
        Chain outer = new Chain(TARGET, () -> {
            Object result = hook.intercept(nested);
            assertEquals(0, installed.get());
            assertEquals(0, queue.tasks.size());
            return result;
        });
        assertSame(Fixture.class, hook.intercept(outer));
        assertEquals(1, outer.proceeded);
        assertEquals(1, nested.proceeded);
        assertEquals(0, nested.argumentsRead);
        assertEquals(0, installed.get());
        assertEquals(1, queue.tasks.size());
        queue.drain();
        assertEquals(1, installed.get());
    }

    @Test public void originalFailureIsUnchangedAndGuardIsCleared() throws Throwable {
        Queue queue = new Queue();
        PluginClassLoaderDiscovery hook = hook(queue, new AtomicInteger(), new AtomicInteger());
        ClassNotFoundException expected = new ClassNotFoundException("original failure");
        Chain failed = new Chain(TARGET, () -> { throw expected; });
        try { hook.intercept(failed); fail("The original exception must propagate"); }
        catch (ClassNotFoundException actual) { assertSame(expected, actual); }
        assertEquals(1, failed.proceeded);
        hook.intercept(new Chain(TARGET, () -> Fixture.class));
        assertEquals(1, queue.tasks.size());
    }

    @Test public void schedulerFailureDoesNotChangeLoadedClassOrPreventLaterDiscovery() throws Throwable {
        Queue queue = new Queue();
        AtomicInteger calls = new AtomicInteger();
        PluginClassLoaderDiscovery hook = hook(task -> {
            if (calls.getAndIncrement() == 0) throw new IllegalStateException("queue unavailable");
            return queue.post(task);
        }, new AtomicInteger(), new AtomicInteger());
        Chain first = new Chain(TARGET, () -> Fixture.class);
        assertSame(Fixture.class, hook.intercept(first));
        assertEquals(1, first.proceeded);
        hook.intercept(new Chain(TARGET, () -> Fixture.class));
        assertEquals(1, queue.tasks.size());
    }

    @Test public void rejectedPostClearsPendingAndInlineSchedulerCannotInstallInLoadClass() throws Throwable {
        AtomicInteger installed = new AtomicInteger();
        AtomicInteger posts = new AtomicInteger();
        PluginClassLoaderDiscovery rejected = hook(task -> { posts.incrementAndGet(); return false; }, installed, new AtomicInteger());
        rejected.intercept(new Chain(TARGET, () -> Fixture.class));
        rejected.intercept(new Chain(TARGET, () -> Fixture.class));
        assertEquals(2, posts.get());
        PluginClassLoaderDiscovery inline = hook(task -> { task.run(); return true; }, installed, new AtomicInteger());
        assertSame(Fixture.class, inline.intercept(new Chain(TARGET, () -> Fixture.class)));
        assertEquals(0, installed.get());
    }

    @Test public void firstUseHelperInitializationCannotReenterDiscoveryMatching() throws Throwable {
        Queue queue = new Queue();
        PluginClassLoaderDiscovery hook = hook(queue, new AtomicInteger(), new AtomicInteger());
        Chain nested = new Chain("kotlin.text.FirstUseFixture", () -> Fixture.class);
        helperInitialization = () -> hook.intercept(nested);
        Chain first = new Chain(TARGET, () -> Fixture.class) {
            @Override public Object getArg(int index) {
                // Accessing the helper for the first time initializes it here, just as
                // a lazily loaded string helper did inside the production callback.
                assertEquals("initialized", LazyHelper.VALUE);
                return super.getArg(index);
            }
        };
        try { assertSame(Fixture.class, hook.intercept(first)); }
        finally { helperInitialization = null; }
        assertEquals(1, nested.proceeded);
        assertEquals(0, nested.argumentsRead);
        assertEquals(1, queue.tasks.size());
    }

    @Test public void concurrentThreadsKeepIndependentGuardsAndDeduplicateByLoaderIdentity() throws Exception {
        Queue queue = new Queue();
        AtomicInteger installed = new AtomicInteger();
        PluginClassLoaderDiscovery hook = hook(queue, installed, new AtomicInteger());
        CountDownLatch insideOriginal = new CountDownLatch(2);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var work = (java.util.concurrent.Callable<Void>) () -> {
                Chain nested = new Chain(TARGET, () -> Fixture.class);
                Chain outer = new Chain(TARGET, () -> {
                    insideOriginal.countDown();
                    assertTrue(insideOriginal.await(2, TimeUnit.SECONDS));
                    return hook.intercept(nested);
                });
                try { hook.intercept(outer); } catch (Throwable error) { throw new AssertionError(error); }
                assertEquals(1, nested.proceeded);
                assertEquals(0, nested.argumentsRead);
                assertEquals(1, outer.argumentsRead);
                return null;
            };
            var first = workers.submit(work);
            var second = workers.submit(work);
            first.get(3, TimeUnit.SECONDS); second.get(3, TimeUnit.SECONDS);
        } finally { workers.shutdownNow(); }
        assertEquals(1, queue.tasks.size());
        queue.drain();
        assertEquals(1, installed.get());
    }

    @Test public void failingInstallationReportsOnceAndDoesNotCreateRetryStorm() throws Throwable {
        Queue queue = new Queue();
        AtomicInteger failures = new AtomicInteger();
        PluginClassLoaderDiscovery hook = new PluginClassLoaderDiscovery(queue, new PluginClassLoaderDiscovery.Listener() {
            @Override public void onDiscovered(ClassLoader loader) { throw new IllegalStateException("adapter unavailable"); }
            @Override public void onFailure(Throwable error) { failures.incrementAndGet(); throw new IllegalStateException("logger unavailable"); }
        });
        hook.intercept(new Chain(TARGET, () -> Fixture.class));
        queue.drain();
        for (int i = 0; i < 20; i++) hook.intercept(new Chain(TARGET, () -> Fixture.class));
        assertEquals(1, failures.get());
        assertEquals(0, queue.tasks.size());
    }

    @Test public void unrelatedNamesAndBootstrapClassesDoNotScheduleAdapters() throws Throwable {
        Queue queue = new Queue();
        PluginClassLoaderDiscovery hook = hook(queue, new AtomicInteger(), new AtomicInteger());
        assertSame(Fixture.class, hook.intercept(new Chain("kotlin.text.StringsKt", () -> Fixture.class)));
        assertSame(String.class, hook.intercept(new Chain(TARGET, () -> String.class)));
        assertEquals(0, queue.tasks.size());
    }

    private static PluginClassLoaderDiscovery hook(PluginClassLoaderDiscovery.Scheduler scheduler,
                                                   AtomicInteger installed, AtomicInteger failures) {
        return new PluginClassLoaderDiscovery(scheduler, new PluginClassLoaderDiscovery.Listener() {
            @Override public void onDiscovered(ClassLoader loader) { installed.incrementAndGet(); }
            @Override public void onFailure(Throwable error) { failures.incrementAndGet(); }
        });
    }

    private static final class Queue implements PluginClassLoaderDiscovery.Scheduler {
        final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        @Override public boolean post(Runnable task) { return tasks.add(task); }
        void drain() { Runnable task; while ((task = tasks.poll()) != null) task.run(); }
    }

    @FunctionalInterface private interface Original { Object run() throws Throwable; }
    private static Original helperInitialization;
    private static final class LazyHelper {
        static final String VALUE;
        static {
            try { helperInitialization.run(); } catch (Throwable error) { throw new AssertionError(error); }
            VALUE = "initialized";
        }
    }
    private static final class Fixture { }

    private static class Chain implements XposedInterface.Chain {
        final String name;
        final Original original;
        int proceeded;
        int argumentsRead;
        Chain(String name, Original original) { this.name = name; this.original = original; }
        @Override public Object proceed() throws Throwable { proceeded++; return original.run(); }
        @Override public Object getArg(int index) { argumentsRead++; return name; }
        @Override public Executable getExecutable() { throw new UnsupportedOperationException(); }
        @Override public Object getThisObject() { return null; }
        @Override public List<Object> getArgs() { return List.of(name); }
        @Override public Object proceed(Object[] args) { throw new UnsupportedOperationException(); }
        @Override public Object proceedWith(Object receiver) { throw new UnsupportedOperationException(); }
        @Override public Object proceedWith(Object receiver, Object[] args) { throw new UnsupportedOperationException(); }
    }
}
