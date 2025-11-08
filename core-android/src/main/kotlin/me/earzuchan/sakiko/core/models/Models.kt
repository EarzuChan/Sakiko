package me.earzuchan.sakiko.core.models

import me.earzuchan.sakiko.api.hook.HookConfig
import de.robv.android.xposed.XC_MethodHook
import me.earzuchan.sakiko.api.hook.HookHandle
import me.earzuchan.sakiko.api.hook.SakikoHookPriority
import java.lang.reflect.Member

data class TokenImpl(val member: Member, val config: HookConfig)

data class HookEntity(
    val member: Member,
    val xposedUnhook: XC_MethodHook.Unhook,
    val callbacks: MutableList<CallbackEntry> = mutableListOf()
)

data class CallbackEntry(
    val config: HookConfig,
    val handle: HookHandle<TokenImpl>,
    val priority: SakikoHookPriority = SakikoHookPriority.DEFAULT
)