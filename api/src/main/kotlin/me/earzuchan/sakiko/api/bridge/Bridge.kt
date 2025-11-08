@file:Suppress("NewApi")

package me.earzuchan.sakiko.api.bridge

import me.earzuchan.sakiko.api.hook.HookConfig
import me.earzuchan.sakiko.api.hook.HookHandle
import me.earzuchan.sakiko.api.hook.HookParam
import me.earzuchan.sakiko.api.hook.SakikoHookPriority
import me.earzuchan.sakiko.api.utils.HookTargetValidateCheckUtils
import me.earzuchan.sakiko.api.utils.SLog
import java.lang.reflect.InvocationTargetException
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

    fun handleHookedMethod(man: Member, snapshot: List<HookConfig>, param: HookParam): Any? {
        val TAG = "HandleHookedMethod"

        // 1. 执行 Before 或 Replace 钩子
        SLog.debug("执行【$man】的${snapshot.size}个钩子", TAG)
        // if (instance != null) SLog.debug("实例：${instance.hashCode()}") TIPS：都是最后返回的实例，而不是callOri的实例

        // 修正：不再使用简单的计数器，而是创建一个列表来存储成功执行了的钩子
        val executedConfigs = mutableListOf<HookConfig>()

        for (config in snapshot) {
            try {
                SLog.debug("该送客了，执行前或换：${config.beforeLambda}、${config.replaceLambda}", TAG)

                if (config.beforeLambda != null) {
                    config.beforeLambda!!.invoke(param)
                } else if (config.replaceLambda != null) {
                    runCatching {
                        param.result = config.replaceLambda!!.invoke(param)
                    }.onFailure {
                        param.throwable = it.cause ?: it // 同样是Invocation的拆箱
                    }
                }
            } catch (t: Throwable) {
                SLog.error("这钩子干脆投降算了：$man\n${t.stackTraceToString()}", TAG)
                param._result = null
                param._throwable = null
                param.earlyReturn = false
                continue
            }

            // 修正：只有当钩子成功执行（没有进入catch块）时，才将其添加到新列表中
            executedConfigs.add(config)

            // 如果早退，replace能不能叠加？还是要另案处理
            if (param.earlyReturn) {
                SLog.debug("要早早离场，剩下的钩子拜拜喵")
                break
            }
        }

        // 2. 执行 Original 方法
        if (!param.earlyReturn) {
            SLog.debug("执行原始：$man", TAG)
            runCatching {
                // CHECK：对了，顺便看看callOri，是用原始参数还是即时（可能被改过）的参数
                try {
                    param.result = param.invokeOriginal(*param.args)
                } catch (e: InvocationTargetException) {
                    param.throwable = e.cause ?: e
                }
            }.onFailure {
                // 捕获其他类型的异常
                if (it !is InvocationTargetException) param.throwable = it
            }
        }

        // 3. 执行 After 钩子 (逆序)

        // 修正：现在我们直接遍历那个只包含成功钩子的列表的逆序版本
        // 这样就完美确保了只有 before/replace 成功的钩子，其 after 才会执行
        for (config in executedConfigs.reversed()) {
            SLog.debug("执行后：${config.afterLambda}", TAG)

            val lastResult = param._result
            val lastThrowable = param.throwable

            try {
                config.afterLambda?.invoke(param)
            } catch (t: Throwable) {
                SLog.error("有个后钩子有问题啊：$man\n${t.stackTraceToString()}", TAG)
                param._result = lastResult
                param._throwable = lastThrowable
            }
        }

        // 4. 返回结果或抛出异常
        SLog.debug("一点薄礼（返回或抛出）：$man", TAG)
        param.throwable?.let { throw it }

        val result = param.result

        SLog.debug("别看我（Hook最终返回值）了，专注战斗：$result", TAG)
        // TODO：后处理和cast

        return result
    }
}