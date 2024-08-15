package me.earzuchan.sakiko.api.reflecting

import java.lang.reflect.Method

fun Class<*>.method(configure: MethodConfig.() -> Unit): Method {
    val config = MethodConfig().apply(configure)
    return this.getDeclaredMethod(config.name, *config.params.toTypedArray())
}

class MethodConfig {
    lateinit var name: String
    val params = mutableListOf<Class<*>>()

    fun param(param: Class<*>) {
        params.add(param)
    }
}

fun String.toClass(): Class<*> {
    return Class.forName(this)
}