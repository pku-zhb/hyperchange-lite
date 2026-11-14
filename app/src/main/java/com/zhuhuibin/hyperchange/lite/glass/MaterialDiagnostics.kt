// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.glass

import com.zhuhuibin.hyperchange.lite.HookRuntime
import java.io.PrintWriter

/** An explicit read-only dump, without a broadcast receiver or notification contents. */
internal object MaterialDiagnostics {
    private const val ARGUMENT = "hc-lite-material"

    fun install(runtime: HookRuntime, argument: String = ARGUMENT, snapshot: () -> String) {
        if (runtime.packageName != "com.android.systemui") return
        runtime.type("com.android.systemui.SystemUIService")?.declaredMethods?.filter {
            it.name == "dump" && it.parameterTypes.contentEquals(arrayOf(
                java.io.FileDescriptor::class.java, PrintWriter::class.java, Array<String>::class.java,
            ))
        }?.forEach { method ->
            runtime.hook(method, "lite-material:diagnostic-dump:$argument") { chain ->
                val args = chain.getArg(2) as? Array<*>
                if (args?.contentEquals(arrayOf(argument)) != true) return@hook chain.proceed()
                val writer = chain.getArg(1) as? PrintWriter ?: return@hook chain.proceed()
                writer.println(snapshot())
                null
            }
        }
    }
}
