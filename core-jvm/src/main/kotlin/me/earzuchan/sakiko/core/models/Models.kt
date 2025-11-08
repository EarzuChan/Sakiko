package me.earzuchan.sakiko.core.models

import me.earzuchan.sakiko.api.hook.HookConfig
import me.earzuchan.sakiko.api.hook.HookHandle
import me.earzuchan.sakiko.api.hook.SakikoHookPriority
import java.lang.reflect.Member
import java.util.concurrent.CopyOnWriteArrayList

data class TokenImpl(val hookId: Long, val config: HookConfig)

data class HookEntity(
    val member: Member,
    val callbacks: CopyOnWriteArrayList<CallbackEntry> = CopyOnWriteArrayList()
)

data class CallbackEntry(
    val config: HookConfig,
    val handle: HookHandle<TokenImpl>,
    val priority: SakikoHookPriority = SakikoHookPriority.DEFAULT
)