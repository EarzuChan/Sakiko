package me.earzuchan.sakiko.core

import me.earzuchan.sakiko.api.bridge.SakiBridge
import me.earzuchan.sakiko.api.hook.HookConfig
import me.earzuchan.sakiko.api.hook.HookHandle
import me.earzuchan.sakiko.api.hook.SakikoHookPriority
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
        TODO("Not yet implemented")
    }

    override fun coreUnhook(handle: HookHandle<TokenImpl>) {
        TODO("Not yet implemented")
    }
}