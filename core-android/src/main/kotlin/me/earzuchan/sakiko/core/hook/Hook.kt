package me.earzuchan.sakiko.core.hook

import de.robv.android.xposed.XposedBridge
import me.earzuchan.sakiko.api.hook.HookParam
import java.lang.reflect.Member

internal class HookParamImpl(member: Member, instance: Any?, args: Array<Any?>) : HookParam(member, instance, args) {
    override fun invokeOriginal(vararg neoArgs: Any?): Any? =
        XposedBridge.invokeOriginalMethod(member, instance, neoArgs)
}