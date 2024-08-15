package me.earzuchan.sakiko.api

import java.lang.reflect.Method

// 神金
fun <T> getNIL(): T? {
    return null
}

public abstract class SakikoBridge {
    abstract fun hook(method: Method, config: HookConfig)

    abstract fun unhook(method: Method)

    companion object {
        @JvmStatic
        var INSTANCE: SakikoBridge? = getNIL()
    }


}

public abstract class SakikoBaseModule {
    abstract fun onHook()
}

fun Method.hook(configure: HookConfig.() -> Unit): UnhookConfig {
    val config = HookConfig().apply(configure)

    SakikoBridge.INSTANCE!!.hook(this, config)

    return UnhookConfig(this)
}

class UnhookConfig(private val method: Method) {
    fun unhook() {
        SakikoBridge.INSTANCE!!.unhook(method)
    }
}

class HookConfig {
    var beforeLambda: (HookContext.() -> Unit)? = null
    var afterLambda: (HookContext.() -> Unit)? = null

    fun before(action: HookContext.() -> Unit) {
        beforeLambda = action
    }

    fun after(action: HookContext.() -> Unit) {
        afterLambda = action
    }
}

class HookContext(val args: Array<Any?>) {

}