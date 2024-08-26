package me.earzuchan.sakiko.api

import java.lang.reflect.Executable

object SakikoAPI {
    const val VERSION = me.earzuchan.sakiko.api.VERSION

    object Config {
    }
}

public class ApiConfig {
}

public abstract class SakikoBridge {
    abstract fun hook(executable: Executable, config: HookConfig)

    abstract fun unhook(executable: Executable)

    companion object {
        @JvmStatic
        lateinit var INSTANCE: SakikoBridge
    }
}

public abstract class SakikoModule(val moduleContext: ModuleContext) {
    abstract fun onHook()

    public class ModuleContext {
    }
}

fun Executable.hook(configure: HookConfig.() -> Unit): UnhookConfig {
    val config = HookConfig().apply(configure)

    SakikoBridge.INSTANCE.hook(this, config)

    return UnhookConfig(this)
}

class UnhookConfig(private val executable: Executable) {
    fun unhook() = SakikoBridge.INSTANCE.unhook(executable)
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
    var result: Any?
        get() {
            throw UnsupportedOperationException("Cannot get result in before hook")
        }
        set(value) {
            throw UnsupportedOperationException("Cannot set result in before hook")
        }
}