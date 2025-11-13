@file:SuppressLint("NewApi") // TODO：说是有api24，大于我们的最低21

package me.earzuchan.sakiko.core

import android.annotation.SuppressLint
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import me.earzuchan.sakiko.api.bridge.SakiBridge
import me.earzuchan.sakiko.api.hook.HookConfig
import me.earzuchan.sakiko.api.hook.HookHandle
import me.earzuchan.sakiko.api.hook.SakikoHookPriority
import me.earzuchan.sakiko.api.utils.SLog
import me.earzuchan.sakiko.core.hook.HookParamImpl
import me.earzuchan.sakiko.core.models.CallbackEntry
import me.earzuchan.sakiko.core.models.HookEntity
import me.earzuchan.sakiko.core.models.TokenImpl
import java.lang.reflect.Member
import java.util.concurrent.ConcurrentHashMap

/* TIPS：暂不启用
internal object SakiNative {
    init {
        System.loadLibrary("sakiko")
    }
}*/

fun initCore() = SakiBridgeImpl.init()

internal object SakiBridgeImpl : SakiBridge<TokenImpl>() {
    fun init() = setInstance(this)

    // ==================== 注册表 ====================

    /** Member -> HookEntity 的索引,已处理的方法和它对应的上下文 */
    private val hookRegistry = ConcurrentHashMap<Member, HookEntity>()

    // ==================== 核心Hook流程 ====================
    override fun coreHook(man: Member, config: HookConfig, priority: SakikoHookPriority): HookHandle<TokenImpl> {
        val TAG = "SBI_CoreHook"

        // 获取或创建Hook实体：首次会创建 Xposed hook
        val entity = hookRegistry.computeIfAbsent(man) { member ->
            val unhook = XposedBridge.hookMethod(member, object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any? =
                    handleHookedMethodAndroid(member, param)
            })

            HookEntity(member, unhook)
        }

        // 添加回调
        synchronized(entity.callbacks) {
            require(entity.callbacks.none { it.config == config }) {
                "这个回调已经肘赢过: ${man.name}"
            }

            val handle = HookHandle(TokenImpl(man, config))
            val entry = CallbackEntry(config, handle, priority)

            // 找第一个优先级小于当前优先级的位置(ordinal更大)
            val insertIndex = entity.callbacks.indexOfFirst { it.priority > priority }
                .takeIf { it >= 0 } ?: entity.callbacks.size
            entity.callbacks.add(insertIndex, entry)

            SLog.debug("客人到了：$man；Priority：$priority；CCB：${entity.callbacks.size}", TAG)

            return handle
        }
    }

    // ==================== 核心Unhook流程 ====================

    override fun coreUnhook(handle: HookHandle<TokenImpl>) {
        val TAG = "SBI_CoreUnhook"

        val token = handle.token
        val man = token.member

        val ctx = hookRegistry[man] ?: run {
            SLog.warn("找不到${man}的上下文", TAG)
            return
        }

        synchronized(ctx.callbacks) {
            // 精确移除指定config的回调
            if (ctx.callbacks.removeIf { it.config == token.config }) {
                val remaining = ctx.callbacks.size
                SLog.debug("成功：${ctx.member}，还有多余资金：$remaining", TAG)

                // 所有回调移除后，清理HookContext
                if (remaining == 0) {
                    hookRegistry.remove(man)
                    ctx.xposedUnhook.unhook()
                    SLog.debug("${man}：它们（回调）走不了了", TAG)
                }
            } else SLog.warn("没找到${man}的该配置项，真奇怪", TAG)
        }
    }

    // ==================== 流程控制 ====================

    fun handleHookedMethodAndroid(man: Member, oriParam: MethodHookParam): Any? {
        val TAG = "SBI_HandleHookedMethodAndroid"

        // 创建参数对象
        val param = HookParamImpl(man, oriParam.thisObject, oriParam.args)

        val entity = hookRegistry[man] ?: return param.callOriginal().also {
            SLog.warn("这这不能，怎么会没上下文：$man", TAG)
        }

        val snapshot = synchronized(entity.callbacks) { entity.callbacks.map { it.config } }

        return handleHookedMethod(man, snapshot, param)
    }
}