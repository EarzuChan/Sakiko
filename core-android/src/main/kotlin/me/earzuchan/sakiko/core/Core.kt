package me.earzuchan.sakiko.core

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XCallback
import me.earzuchan.sakiko.api.bridge.SakiBridge
import me.earzuchan.sakiko.api.hook.HookConfig
import me.earzuchan.sakiko.api.hook.HookHandle
import me.earzuchan.sakiko.api.hook.HookParam
import me.earzuchan.sakiko.api.hook.SakikoHookPriority
import me.earzuchan.sakiko.api.hook.SakikoHookPriority.*
import me.earzuchan.sakiko.core.models.TokenImpl
import java.lang.reflect.Member

/* TIPS：暂不启用
internal object SakiNative {
    init {
        System.loadLibrary("sakiko")
    }
}*/

fun initCore() = SakiBridgeImpl.init()

internal object SakiBridgeImpl : SakiBridge<TokenImpl>() {
    fun init() = setInstance(this)

    override fun coreHook(man: Member, config: HookConfig, priority: SakikoHookPriority): HookHandle<TokenImpl> {
        val unhook = XposedBridge.hookMethod(man, object : XC_MethodHook(priority.xp) {
            private val isReplace = config.replaceLambda != null

            override fun beforeHookedMethod(param: MethodHookParam) {
                val myParam = param.wrap()

                if (isReplace) {
                    runCatching {
                        param.result = config.replaceLambda!!.invoke(myParam)
                    }.onFailure {
                        param.throwable = it
                    }
                } else if (config.beforeLambda != null) config.beforeLambda!!.invoke(myParam)
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                if (!isReplace) config.afterLambda?.invoke(param.wrap())
            }
        })

        return HookHandle(TokenImpl(unhook))
    }

    override fun coreUnhook(handle: HookHandle<TokenImpl>) {
        TODO("Not yet implemented")
    }

    private fun MethodHookParam.wrap(): HookParam = object : HookParam() {
        override val instance: Any? get() = thisObject

        override val member: Member get() = method

        override val args: Array<Any?> get() = this@wrap.args

        override var result: Any?
            get() = this@wrap.result
            set(value) {
                this@wrap.result = value
            }

        override var throwable: Throwable?
            get() = this@wrap.throwable
            set(value) {
                this@wrap.throwable = value
            }

        override fun callOriginal(): Any? = XposedBridge.invokeOriginalMethod(method, thisObject, this@wrap.args)

        override fun invokeOriginal(vararg args: Any?): Any? =
            XposedBridge.invokeOriginalMethod(method, thisObject, args)
    }

    private val SakikoHookPriority.xp
        get() = when (this) {
            HIGHEST -> XCallback.PRIORITY_HIGHEST
            DEFAULT -> XCallback.PRIORITY_DEFAULT
            LOWEST -> XCallback.PRIORITY_LOWEST
        }
}