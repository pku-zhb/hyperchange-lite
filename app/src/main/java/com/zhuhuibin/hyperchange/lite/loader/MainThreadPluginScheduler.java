// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.loader;

import android.os.Handler;

/** Keep even the enqueue operation free of Kotlin function wrappers. */
public final class MainThreadPluginScheduler implements PluginClassLoaderDiscovery.Scheduler {
    private final Handler handler;

    public MainThreadPluginScheduler(Handler handler) { this.handler = handler; }

    @Override public boolean post(Runnable task) { return handler.post(task); }
}
