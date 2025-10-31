package me.earzuchan.sakiko.api.module

import com.highcapable.kavaref.resolver.MethodResolver
import com.highcapable.kavaref.extension.toClass as kToClz

interface SakikoModule {
    fun onInit() {}

    fun onHook()
}

// TODO
class SakikoContext {
    val appClassLoader: ClassLoader = TODO()

    fun String.toClass(classLoader: ClassLoader = appClassLoader) = kToClz(classLoader)
}

fun SakikoModule.encase(initiate: SakikoContext.() -> Unit) {
    TODO()
}
