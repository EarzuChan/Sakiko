package me.earzuchan.sakiko.core.hook

import me.earzuchan.sakiko.api.hook.HookParam
import me.earzuchan.sakiko.core.SakiBridgeImpl.shouldInvokeOrigin
import me.earzuchan.sakiko.core.utils.RefX.proInvoke
import java.lang.reflect.Member

internal class HookParamImpl(member: Member, instance: Any?, args: Array<Any?>) : HookParam(member, instance, args) {
    override fun invokeOriginal(vararg neoArgs: Any?): Any? {
        shouldInvokeOrigin.set(true)

        // TIPS：已证明改参有用

        // 在本地调用
        return member.proInvoke(instance, *neoArgs)
    }
}