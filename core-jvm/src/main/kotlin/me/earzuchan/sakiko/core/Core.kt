package me.earzuchan.sakiko.core

import com.highcapable.kavaref.KavaRef.Companion.resolve
import me.earzuchan.sakiko.api.bridge.SakiBridge
import me.earzuchan.sakiko.api.hook.HookConfig
import me.earzuchan.sakiko.api.hook.HookHandle
import me.earzuchan.sakiko.api.hook.HookParam
import me.earzuchan.sakiko.api.hook.hook
import me.earzuchan.sakiko.api.utils.SLog
import me.earzuchan.sakiko.core.utils.ByteCodeStorage
import me.earzuchan.sakiko.core.utils.ByteCodeVerifier
import me.earzuchan.sakiko.core.utils.ByteCodeWeaver
import me.earzuchan.sakiko.core.utils.InvokeHelper.invokeUnwraply
import me.earzuchan.sakiko.core.utils.NativeUtils
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Member
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

internal object SakiNative {
    init {
        NativeUtils.loadLibrary("sakiko")
    }

    @JvmStatic
    external fun getClassByteCode(targetClass: Class<*>): ByteArray

    @JvmStatic
    external fun redefineClass(targetClass: Class<*>, newByteCode: ByteArray, shouldBypassVerification: Boolean)

    @JvmStatic
    external fun test1()

    @JvmStatic
    external fun test2()
}


data class TokenImpl(
    val hookId: Long,
    val config: HookConfig
)

internal object SakiBridgeImpl : SakiBridge<TokenImpl>() {
    /*init {
        setInstance(this)
    }*/

    fun init() = setInstance(this)

    val lock = Object()

    /** Hook项：包含方法信息和所有回调 */
    data class HookEntity(
        val member: Member,
        // CopyOnWriteArrayList: 读多写少场景的最佳选择
        val callbacks: CopyOnWriteArrayList<CallbackEntry> = CopyOnWriteArrayList()
    )


    data class CallbackEntry(
        val config: HookConfig,
        val handle: HookHandle<TokenImpl>
    )

    // ==================== 注册表 ====================

    /** hookId -> 完整的Hook上下文 */
    private val hookRegistry = ConcurrentHashMap<Long, HookEntity>()

    /** Member -> hookId 的索引，用于快速查找是否已hook */
    private val methodIndex = ConcurrentHashMap<Member, Long>()

    /** hookId分配器 */
    private val hookIdAllocator = AtomicLong(1)

    // ==================== 核心Hook流程 ====================

    override fun coreHook(man: Member, config: HookConfig): HookHandle<TokenImpl> {
        // 获取或分配hookId（首次hook时织入字节码）
        val hookId = methodIndex.computeIfAbsent(man) {
            val id = hookIdAllocator.getAndIncrement()
            process(it, id)  // 织入字节码，传入hookId

            // 创建并注册HookContext
            hookRegistry[id] = HookEntity(it)

            id
        }

        // 获取或创建Hook上下文
        val context = requireNotNull(hookRegistry[hookId]) {
            "BUG：哥们儿实体【ID：$hookId】怎么被肘没了"
        }

        // 第三步：添加回调（线程安全）
        synchronized(context.callbacks) {
            // 检查重复hook
            require(context.callbacks.none { it.config == config }) {
                "这个回调已经肘赢过$man"
            }

            // 创建token和handle
            val handle = HookHandle(TokenImpl(hookId, config))

            // 添加到callbacks列表
            context.callbacks.add(CallbackEntry(config, handle))

            SLog.debug("Hook：$man；ID：$hookId；CCB：${context.callbacks.size}")

            return handle
        }
    }

    // ==================== Unhook流程 ====================

    override fun coreUnhook(handle: HookHandle<TokenImpl>) {
        val token = handle.token
        val hookId = token.hookId

        val context = hookRegistry[hookId] ?: run {
            SLog.warn("Unhook: Hook context not found for hookId=$hookId")
            return
        }

        synchronized(context.callbacks) {
            // 精确移除指定config的回调
            if (context.callbacks.removeIf { it.config == token.config }) {
                val remaining = context.callbacks.size
                SLog.debug("Unhook success: ${context.member}, remaining=$remaining")

                // 🗑️ 所有回调移除后，清理HookContext
                // ⚠️ 注意：memberIndex不清理（字节码无法还原）
                if (remaining == 0) {
                    hookRegistry.remove(hookId)
                    SLog.debug("Context cleaned: hookId=${hookId} (bytecode still there)")
                }
            } else {
                SLog.warn("Unhook: Config not found for hookId=$hookId")
            }
        }
    }

    // ==================== 字节码织入 ====================

    /**
     * 织入字节码（仅在首次hook该方法时调用）
     * @param man 被hook的方法
     * @param hookId 分配的唯一ID，会织入字节码中作为常数
     */
    private fun process(man: Member, hookId: Long) {
        val targetClass = man.declaringClass

        synchronized(lock) {
            val oldByteCode = ByteCodeStorage.getByClass(targetClass)

            // 🔑 关键：将hookId织入字节码中
            val newByteCode = ByteCodeWeaver.weave(man, oldByteCode, hookId)
            // TOD：ByteCodeVerifier.verify(newByteCode)

            // TODO：加载修改后的字节码

            SLog.debug("Process: Wove bytecode for $man with hookId=$hookId")

            // 如果是实例构造器（要在第一行Call Super的），就要绕过校验器
            val shouldBypassVerification = man is Constructor<*> && !Modifier.isStatic(man.modifiers)

            SakiNative.redefineClass(targetClass, newByteCode, shouldBypassVerification)
        }
    }

    /**
     * 从中转跳板调用此方法
     * 被织入的Prologue代码会将hookId和参数传过来
     */
    @JvmStatic
    fun handleHookedMethod(entity: HookEntity, instance: Any?, args: Array<Any?>): Any? {
        val man = entity.member

        // 创建参数对象
        val param = HookParamImpl(man, instance, args)

        // 📸 快照：避免迭代时被并发修改 CHECK：欲何为
        val snapshot = entity.callbacks.toList()

        // 1. 执行 Before 或 Replace 钩子
        SLog.debug("Executing Before or Replace hooks for $man, callback count=${snapshot.size}")

        // 修正：不再使用简单的计数器，而是创建一个列表来存储成功执行了的钩子
        val executedHandles = mutableListOf<CallbackEntry>()

        for (entry in snapshot) {
            try {
                val config = entry.config

                SLog.debug("执行前或换：${config.beforeLambda}、${config.replaceLambda}")

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
                SLog.error("有个前钩子有问题啊：$man\n${t.stackTraceToString()}")
                param._result = null
                param._throwable = null
                param.earlyReturn = false
                continue
            }

            // 修正：只有当钩子成功执行（没有进入catch块）时，才将其添加到新列表中
            executedHandles.add(entry)

            if (param.earlyReturn) {
                SLog.debug("要早早离场，剩下的钩子拜拜喵")
                break
            }
        }

        // 2. 执行 Original 方法
        if (!param.earlyReturn) {
            SLog.debug("执行原始：$man")
            runCatching {
                // 修改：原代码中 originalOne.call() 的异常没有正确处理
                // InvocationTargetException 需要解包才能获得真正的异常
                // CHECK：对了，顺便看看callOri，是用原始参数还是即时（可能被改过）的参数
                try {
                    param.result = param.invokeOriginal(*args)
                } catch (e: InvocationTargetException) {
                    // 模仿LSPosed，从InvocationTargetException中获取真正的cause
                    param.throwable = e.cause ?: e
                }
            }.onFailure {
                // 捕获其他类型的异常，例如调用.call()本身的错误
                if (it !is InvocationTargetException) param.throwable = it
            }
        }

        // 3. 执行 After 钩子 (逆序)
        SLog.debug("执行后：$man")

        // 修正：现在我们直接遍历那个只包含成功钩子的列表的逆序版本
        // 这样就完美确保了只有 before/replace 成功的钩子，其 after 才会执行
        for (handle in executedHandles.reversed()) {
            val lastResult = param._result
            val lastThrowable = param.throwable

            try {
                val config = handle.config
                config.afterLambda?.invoke(param)
            } catch (t: Throwable) {
                SLog.error("有个后钩子有问题啊：$man\n${t.stackTraceToString()}")
                param._result = lastResult
                param._throwable = lastThrowable
            }
        }

        // 4. 返回结果或抛出异常
        SLog.debug("返回或者抛出就完事了：$man")
        param.throwable?.let { throw it }
        return param.result
    }

    val shouldInvokeOrigin: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    @JvmStatic
    fun relay(idThisArgs: Array<Any?>): Array<Any?>? {
        val hookId = idThisArgs[0] as Long

        val entity = hookRegistry[hookId]

        // 检查该不该执行原代码：钩子都没了，或者flag
        if (entity == null || shouldInvokeOrigin.get()) {
            shouldInvokeOrigin.set(false)
            // 返回null，织入就会继续执行原代码
            return null
        }

        val isStatic = (entity.member.modifiers and Modifier.STATIC) != 0

        val args = idThisArgs.drop(if (isStatic) 1 else 2).toTypedArray()

        // invoke the hook callback
        return arrayOf(handleHookedMethod(entity, if (isStatic) null else idThisArgs[1], args))
    }

    internal class HookParamImpl(
        override val member: Member,
        override var instance: Any?,
        override val args: Array<Any?>
    ) : HookParam() {
        internal var _result: Any? = null

        override var result: Any?
            set(value) {
                _result = value
                earlyReturn = true
                _throwable = null // 设置结果时，清除异常 LSP逻辑
            }
            get() = _result

        internal var _throwable: Throwable? = null

        // TODO：如果在BEFORE中设置，应该EARLY RET
        override var throwable: Throwable?
            set(value) {
                _result = null
                earlyReturn = true
                _throwable = value // 设置结果时，清除异常 LSP逻辑
            }
            get() = _throwable

        internal var earlyReturn = false // 内部标志，用于 early return

        /**
         * 调用原始方法。
         * 只能在 replace lambda 中调用。在 before/after 中调用会抛出异常？←CHECK
         */
        override fun callOriginal(): Any? = TODO() // CHECK：用原参数还是新参数，如果对象是引用，那只可能是新参？

        override fun invokeOriginal(vararg args: Any?): Any? {
            shouldInvokeOrigin.set(true)

            // 可能得在本地调用？基本类型要拆箱
            return member.invokeUnwraply(instance, *args)
        }
    }
}

class ForTest {
    fun funkIt(int: Int, str: String) = println("Funk it! Num=$int, text=$str")

    companion object {
        @JvmStatic
        fun ohYeah(int: Int, long: Long, str: String) = println("Oh yeah! Num=$int, Mamba=$long, text=$str")
    }
}


fun main() {
    SakiBridgeImpl.init()

    val clz = ForTest::class.java
    /*var byteCode = SakiNative.getClassByteCode(clz)
    File("bc_ori").writeBytes(byteCode)

    val method1 = clz.resolve().firstMethod { name = "funkIt" }.self
    byteCode = ByteCodeWeaver.weave(method1, byteCode, 114514L)
    val method2 = clz.resolve().firstMethod { name = "ohYeah" }.self
    byteCode = ByteCodeWeaver.weave(method2, byteCode, 1919810L)
    File("bc_new").writeBytes(byteCode)

    SakiNative.redefineClass(clz, byteCode, false)

    byteCode = SakiNative.getClassByteCode(clz)
    File("bc_redef").writeBytes(byteCode)*/

    clz.resolve().apply {
        firstMethod { name = "funkIt" }.hook {
            before {
                println("bef")
            }

            after {
                println("aft")
            }
        }

        firstMethod { name = "ohYeah" }.hook {
            replaceUnit {
                println("just funk it")
            }
        }
    }

    ForTest().funkIt(114, "514")
    ForTest.ohYeah(114, 514L, "1919810")
}