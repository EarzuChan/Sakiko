package me.earzuchan.sakiko.core

import me.earzuchan.sakiko.api.bridge.SakiBridge
import me.earzuchan.sakiko.api.hook.HookConfig
import me.earzuchan.sakiko.api.hook.HookHandle
import me.earzuchan.sakiko.api.hook.SakikoHookPriority
import me.earzuchan.sakiko.api.utils.SLog
import me.earzuchan.sakiko.core.hook.HookParamImpl
import me.earzuchan.sakiko.core.models.CallbackEntry
import me.earzuchan.sakiko.core.models.HookEntity
import me.earzuchan.sakiko.core.models.TokenImpl
import me.earzuchan.sakiko.core.utils.ByteCodeStorage
import me.earzuchan.sakiko.core.utils.ByteCodeVerifier
import me.earzuchan.sakiko.core.utils.ByteCodeWeaver.weave
import me.earzuchan.sakiko.core.utils.NativeUtils
import me.earzuchan.sakiko.core.utils.RelayClassUtils
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Member
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal object SakiNative {
    init {
        NativeUtils.loadLibrary("sakiko")
    }

    @JvmStatic
    external fun getClassByteCode(targetClass: Class<*>): ByteArray

    @JvmStatic
    external fun redefineClass(targetClass: Class<*>, newByteCode: ByteArray, shouldBypassVerification: Boolean)

    // 不要直接调用我，最好调用`MiscUtils.getClInitOrNull`
    @JvmStatic
    external fun getClInitOrNull(targetClass: Class<*>): Constructor<*>?

    @JvmStatic
    external fun loadClassToBootstrap(clzName: String, clzBytes: ByteArray): Class<*>

    @JvmStatic
    external fun proInvoke(
        man: Member, sign: String,
        clz: Class<*>, isStatic: Boolean,
        instance: Any?, args: Array<*>
    ): Any?
}

// --开洞方法--

fun initCore() = initCore(false)

fun setCoreRelayBlazzName(relayBlazz: String) {
    SakiBridgeImpl.setRelayBlazzName(relayBlazz)
}

fun initCore(useBootstrapRelayClass: Boolean) {
    SakiBridgeImpl.run {
        init()

        if (useBootstrapRelayClass) setRelayBlazzName(RelayClassUtils.setupBootstrapRelayClass())
    }
}

fun initCore(relayBlazz: String) {
    SakiBridgeImpl.run {
        init()

        setRelayBlazzName(relayBlazz)
    }
}

internal object SakiBridgeImpl : SakiBridge<TokenImpl>() {
    fun init() = setInstance(this)

    // ==================== 注册表 ====================

    /** hookId -> 完整的Hook上下文 */
    private val hookRegistry = ConcurrentHashMap<Long, HookEntity>()

    /** Member -> hookId 的索引，已处理的方法和它对应的hookId */
    private val processedMethodMap = ConcurrentHashMap<Member, Long>()

    /** hookId分配器 */
    private val hookIdAllocator = AtomicLong(1)

    // ==================== 核心Hook流程 ====================

    override fun coreHook(man: Member, config: HookConfig, priority: SakikoHookPriority): HookHandle<TokenImpl> {
        val TAG = "SBI_CoreHook"

        // 获取或分配hookId（首次hook时织入字节码）
        val hookId = processedMethodMap.computeIfAbsent(man) {
            val id = hookIdAllocator.getAndIncrement()

            man.process(id)

            id
        }

        // 获取或创建Hook上下文
        val context = hookRegistry.computeIfAbsent(hookId) { HookEntity(man) }

        // 第三步：添加回调（线程安全）
        synchronized(context.callbacks) {
            require(context.callbacks.none { it.config == config }) {
                "这个回调已经肘赢过：${man.name}"
            }
            val handle = HookHandle(TokenImpl(hookId, config))
            val entry = CallbackEntry(config, handle, priority)

            // 找第一个优先级小于当前优先级的位置（ordinal更大）
            val insertIndex = context.callbacks.indexOfFirst { it.priority > priority }
                .takeIf { it >= 0 } ?: context.callbacks.size
            context.callbacks.add(insertIndex, entry)

            SLog.debug("客人到了：$man；ID：$hookId；Priority：$priority；CCB：${context.callbacks.size}", TAG)

            return handle
        }
    }

    // ==================== Unhook流程 ====================

    override fun coreUnhook(handle: HookHandle<TokenImpl>) {
        val TAG = "SBI_CoreUnhook"

        val token = handle.token
        val hookId = token.hookId

        val context = hookRegistry[hookId] ?: run {
            SLog.warn("找不到${hookId}的上下文", TAG)
            return
        }

        synchronized(context.callbacks) {
            // 精确移除指定config的回调
            if (context.callbacks.removeIf { it.config == token.config }) {
                val remaining = context.callbacks.size
                SLog.debug("成功：${context.member}，还有多余资金：$remaining", TAG)

                // 🗑️ 所有回调移除后，清理HookContext；processedMethodMap不清理（已实装的字节码无法还原）
                if (remaining == 0) {
                    hookRegistry.remove(hookId)
                    SLog.debug("${hookId}：它们（回调）走不了了", TAG)
                }
            } else SLog.warn("没找到${hookId}的该配置项，真奇怪", TAG)
        }
    }

    // ==================== 方法处理 ====================

    private var relayBlazzName = "me/earzuchan/sakiko/core/SakiBridgeImpl"

    fun setRelayBlazzName(relayBlazz: String) {
        relayBlazzName = relayBlazz.replace('.', '/')
    }

    private fun Member.process(hookId: Long) {
        val TAG = "SBI_MemberProcess"

        synchronized(declaringClass) {
            val oldByteCode = ByteCodeStorage.getByClass(declaringClass)

            val newByteCode = oldByteCode.weave(this, hookId, relayBlazzName)

            ByteCodeVerifier.verify(newByteCode)

            SLog.debug("给${this}处理，hookId：$hookId", TAG)

            ByteCodeStorage.setByClass(declaringClass, newByteCode)

            // 如果是实例构造器（要在第一行Call Super的），就要绕过校验器
            val shouldBypassVerification = this is Constructor<*> && !Modifier.isStatic(modifiers)

            SakiNative.redefineClass(declaringClass, newByteCode, shouldBypassVerification)
        }
    }

    // ==================== 流程控制 ====================

    @JvmStatic
    fun handleHookedMethod(entity: HookEntity, instance: Any?, args: Array<Any?>): Any? {
        val TAG = "SBI_HandleHookedMethod"

        val man = entity.member

        // 创建参数对象
        val param = HookParamImpl(man, instance, args)

        // 快照：避免迭代时被并发修改 CHECK：欲何为
        val snapshot = entity.callbacks.toTypedArray()

        // 1. 执行 Before 或 Replace 钩子
        SLog.debug("执行【$man】的${snapshot.size}个钩子", TAG)
        // if (instance != null) SLog.debug("实例：${instance.hashCode()}") TIPS：都是最后返回的实例，而不是callOri的实例

        // 修正：不再使用简单的计数器，而是创建一个列表来存储成功执行了的钩子
        val executedHandles = mutableListOf<CallbackEntry>()

        for (entry in snapshot) {
            try {
                val config = entry.config

                SLog.debug("该送客了，执行前或换：${config.beforeLambda}、${config.replaceLambda}", TAG)

                if (config.beforeLambda != null) {
                    config.beforeLambda!!.invoke(param)
                } else if (config.replaceLambda != null) {
                    runCatching {
                        param.result = config.replaceLambda!!.invoke(param)
                    }.onFailure {
                        param.throwable = it
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
            executedHandles.add(entry)

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
                    param.result = param.invokeOriginal(*args)
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
        for (handle in executedHandles.reversed()) {
            val config = handle.config
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

        // if (man is Constructor<*>) result = null  这样兜底吗；好像搞不搞都null（void）

        // CHECK：另外，如果void类型，要不要返回null

        // CHECK：如果不是基本类型，要cast一下吗；是基本类型，null怎么办

        return result
    }

    val shouldInvokeOrigin: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    @JvmStatic
    fun relay(idThisArgs: Array<Any?>): Array<Any?>? {
        val hookId = idThisArgs[0] as Long

        val entity = hookRegistry[hookId]

        // 检查该不该执行原代码：钩子都没了，或者flag
        if (entity == null || shouldInvokeOrigin.get()) {
            shouldInvokeOrigin.set(false)
            // 返回null，织入的序言就会继续执行原代码
            return null
        }

        val isStatic = Modifier.isStatic(entity.member.modifiers)

        val args = idThisArgs.drop(if (isStatic) 1 else 2).toTypedArray()

        // invoke the hook callback
        return arrayOf(handleHookedMethod(entity, if (isStatic) null else idThisArgs[1], args))
    }
}