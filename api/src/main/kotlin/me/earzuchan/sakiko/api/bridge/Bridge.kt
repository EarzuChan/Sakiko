@file:Suppress("NewApi")

package me.earzuchan.sakiko.api.bridge

import me.earzuchan.sakiko.api.hook.HookConfig
import me.earzuchan.sakiko.api.hook.HookHandle
import me.earzuchan.sakiko.api.hook.SakikoHookPriority
import me.earzuchan.sakiko.api.utils.HookTargetValidateCheckUtils
import java.lang.reflect.Member

abstract class SakiBridge<TOKEN : Any> {
    // TIPS：管理和执行HOOK/UNHOOK都是各平台的事情

    fun checkAndHook(man: Member, config: HookConfig, priority: SakikoHookPriority): HookHandle<TOKEN> {
        // CHECK：还要不要非空检测

        config.check() // 以防止矛盾的Hook

        HookTargetValidateCheckUtils.check(man)

        return coreHook(man, config, priority)
    }

    fun unhook(handle: HookHandle<TOKEN>) = coreUnhook(handle)

    abstract fun coreHook(man: Member, config: HookConfig, priority: SakikoHookPriority): HookHandle<TOKEN>

    abstract fun coreUnhook(handle: HookHandle<TOKEN>)

    companion object {
        @Volatile
        private var INSTANCE: SakiBridge<out Any>? = null

        fun setInstance(instance: SakiBridge<out Any>) {
            INSTANCE = instance
        }

        fun requireInstance(): SakiBridge<out Any> =
            INSTANCE ?: error("不有可用实例")
    }
}