package me.earzuchan.sakiko.api.module

import me.earzuchan.sakiko.api.utils.SLog
import com.highcapable.kavaref.extension.toClass as kToClz

interface SakikoModuleEntry {
    fun onInit() {}

    fun onHook()
}

val sakiCtxLocal = ThreadLocal<SakikoContext>()

// TODO：Fulfill
class SakikoContext(val appClassLoader: ClassLoader) {
    companion object {
        private const val TAG = "SakikoContext"
    }

    fun String.toClass(classLoader: ClassLoader = appClassLoader): Class<Any> {
        // SLog.debug("成功获得AppClassLoader：$classLoader", TAG)

        return kToClz(classLoader)
    }
}

fun SakikoModuleEntry.encase(initiate: SakikoContext.() -> Unit) {
    val sakiCtx = sakiCtxLocal.get() ?: error("没有上下文")
    sakiCtx.initiate()
}
