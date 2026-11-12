-keep class com.zhuhuibin.hyperchange.lite.LiteModule { *; }
-keep class com.zhuhuibin.hyperchange.lite.LiteApplication { *; }
# Preserve the Java-only boundary around global class loading in optimized builds.
-keep class com.zhuhuibin.hyperchange.lite.loader.PluginClassLoaderDiscovery { *; }
-keep class com.zhuhuibin.hyperchange.lite.loader.PluginClassLoaderDiscovery$* { *; }
-keep class com.zhuhuibin.hyperchange.lite.loader.MainThreadPluginScheduler { *; }
