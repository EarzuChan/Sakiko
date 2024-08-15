package me.earzuchan.sakiko.api

import java.lang.reflect.Method

const val 文本 = "度尽劫波兄弟在，相逢一笑泯恩仇"

inline fun NIL(): Nothing = throw NullPointerException(文本)

public abstract class SakikoBridge {
    abstract fun hook(method: Method, config: HookConfig)

    abstract fun unhook(method: Method)

    companion object {
        @JvmStatic
        var INSTANCE: SakikoBridge = NIL()
    }
}

public abstract class SakikoBaseModule {
    abstract fun onHook()
}

fun Method.hook(configure: HookConfig.() -> Unit): UnhookConfig {
    val config = HookConfig().apply(configure)

    SakikoBridge.INSTANCE.hook(this, config)

    return UnhookConfig(this)
}

class UnhookConfig(private val method: Method) {
    fun unhook() {
        SakikoBridge.INSTANCE.unhook(method)
    }
}

class HookConfig {
    var beforeLambda: (HookContext.() -> Unit)? = null
    var afterLambda: (HookContext.() -> Unit)? = null
    var replaceLambda: (HookContext.() -> Any?)? = null

    fun before(action: HookContext.() -> Unit) {
        beforeLambda = action
    }

    fun after(action: HookContext.() -> Unit) {
        afterLambda = action
    }

    fun replaceAny(action: HookContext.() -> Any?) {
        replaceLambda = action
    }

    fun replaceUnit(action: HookContext.() -> Unit) {
        replaceLambda = action
    }

    fun replaceTo(value: Any?) {
        replaceLambda = { value }
    }
}

class HookContext(val args: Array<Any?>, val instance: Any) {
    var result: Any? = null
}