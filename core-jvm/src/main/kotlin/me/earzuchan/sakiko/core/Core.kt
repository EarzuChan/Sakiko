package me.earzuchan.sakiko.core

import com.highcapable.kavaref.KavaRef.Companion.resolve
import me.earzuchan.sakiko.api.bridge.SakiBridge
import me.earzuchan.sakiko.api.hook.HookConfig
import me.earzuchan.sakiko.api.hook.HookHandle
import me.earzuchan.sakiko.api.hook.HookParam
import me.earzuchan.sakiko.api.hook.SakikoHookPriority
import me.earzuchan.sakiko.api.hook.hook
import me.earzuchan.sakiko.api.utils.SLog
import me.earzuchan.sakiko.core.utils.ByteCodeStorage
import me.earzuchan.sakiko.core.utils.ByteCodeVerifier
import me.earzuchan.sakiko.core.utils.ByteCodeWeaver
import me.earzuchan.sakiko.core.utils.InvokeHelper.invokeUnwraply
import me.earzuchan.sakiko.core.utils.NativeUtils
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

    /** Hook项：包含方法信息和所有回调 */
    data class HookEntity(
        val member: Member,
        val callbacks: CopyOnWriteArrayList<CallbackEntry> = CopyOnWriteArrayList()
    )


    data class CallbackEntry(
        val config: HookConfig,
        val handle: HookHandle<TokenImpl>,
        val priority: SakikoHookPriority = SakikoHookPriority.DEFAULT
    )

    // ==================== 注册表 ====================

    /** hookId -> 完整的Hook上下文 */
    private val hookRegistry = ConcurrentHashMap<Long, HookEntity>()

    /** Member -> hookId 的索引，已处理的方法和它对应的hookId */
    private val processedMethodMap = ConcurrentHashMap<Member, Long>()

    /** hookId分配器 */
    private val hookIdAllocator = AtomicLong(1)

    // ==================== 核心Hook流程 ====================

    override fun coreHook(man: Member, config: HookConfig, priority: SakikoHookPriority): HookHandle<TokenImpl> {
        // TODO：在下面实现priority

        // 获取或分配hookId（首次hook时织入字节码）
        val hookId = processedMethodMap.computeIfAbsent(man) {
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
            require(context.callbacks.none { it.config == config }) {
                "这个回调已经肘赢过$man"
            }
            val handle = HookHandle(TokenImpl(hookId, config))
            val entry = CallbackEntry(config, handle, priority)

            // 找第一个优先级小于当前优先级的位置（ordinal更大）
            val insertIndex = context.callbacks.indexOfFirst { it.priority > priority }
                .takeIf { it >= 0 } ?: context.callbacks.size
            context.callbacks.add(insertIndex, entry)

            SLog.debug("Hook：$man；ID：$hookId；Priority：$priority；CCB：${context.callbacks.size}")

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

                // 🗑️ 所有回调移除后，清理HookContext；processedMethodMap不清理（已实装的字节码无法还原）
                if (remaining == 0) {
                    hookRegistry.remove(hookId)
                    SLog.debug("Context cleaned: hookId=${hookId} (bytecode still there)")
                }
            } else SLog.warn("Unhook: Config not found for hookId=$hookId")
        }
    }

    // ==================== 方法处理 ====================

    private fun process(man: Member, hookId: Long) {
        val targetClass = man.declaringClass

        synchronized(targetClass) {
            val oldByteCode = ByteCodeStorage.getByClass(targetClass)

            // 🔑 关键：将hookId织入字节码中
            val newByteCode = ByteCodeWeaver.weave(man, oldByteCode, hookId)

            ByteCodeVerifier.verify(newByteCode)

            SLog.debug("Process: Wove bytecode for $man with hookId=$hookId")

            // 如果是实例构造器（要在第一行Call Super的），就要绕过校验器
            val shouldBypassVerification = man is Constructor<*> && !Modifier.isStatic(man.modifiers)

            SakiNative.redefineClass(targetClass, newByteCode, shouldBypassVerification)
        }
    }

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

        // CHECK：是否实现类如果在BEFORE中设置，应该EARLY RET
        override var throwable: Throwable?
            set(value) {
                _result = null
                earlyReturn = true
                _throwable = value // 设置结果时，清除异常 LSP逻辑
            }
            get() = _throwable

        internal var earlyReturn = false // 内部标志，用于 early return

        /**
         * 调用原始方法
         */
        // CHECK：用原参数还是新参数，如果对象是引用，那只可能是新参？
        override fun callOriginal(): Any? = invokeOriginal(*args)

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

open class Vater {
    open fun a() = "Fuck you"
    open fun b() = "Suck your Dick"
}

class Sohn : Vater() {
    override fun a() = "Fuck me"
    // NOT OVERRIDIN B
}

class Man {
    fun whatHeCanSay() = "Mamba out"
    fun kobe() = 111
}

fun main() {
    SakiBridgeImpl.init()

    testVaterSohn()
    testMultiAndPriority()
}

fun testVaterSohn() {
    Vater::class.resolve().apply {
        firstMethod { name = "a" }.hook {
            before {
                SLog.debug("bef vater a")
                SLog.debug("result: ${callOriginal()}")
                SLog.debug("aft callin org vater a")
            }
        }

        firstMethod { name = "b" }.hook {
            before {
                SLog.debug("bef vater b")
                SLog.debug("result: ${callOriginal()}")
                SLog.debug("aft callin org vater b")
            }
        }
    }

    Sohn::class.resolve().apply {
        // RESULT：仅仅SOHN
        firstMethod { name = "a" }.hook {
            before {
                SLog.debug("bef sohn a")
                SLog.debug("result: ${callOriginal()}")
                SLog.debug("aft callin org sohn a")
            }
        }

        // RESULT：完全等于Vater的bHook
        firstMethod {
            name = "b"
            superclass() // u kno
        }.hook {
            before {
                SLog.debug("bef sohn b")
                SLog.debug("result: ${callOriginal()}")
                SLog.debug("aft callin org sohn b")
            }
        }
    }

    SLog.info("\n\nvater")
    val vater = Vater()
    vater.a()
    vater.b()

    SLog.info("\n\nsohn")
    val sohn = Sohn()
    sohn.a()
    sohn.b()

    // RESULT：跟直接Sohn的触发结果一样，就是实例本身的类型管用
    SLog.info("\n\njunge")
    val junge = Sohn() as Vater
    junge.a()
    junge.b()
}

fun testMultiAndPriority() {
    val manClass = Man::class.resolve()

    val whatHeCanSayMethod = manClass.firstMethod { name = "whatHeCanSay" }
    val kobeMethod = manClass.firstMethod { name = "kobe" }

    // WCS RESULT：123-321

    whatHeCanSayMethod.hook {
        before {
            SLog.debug("bef wc 1")
        }

        after {
            SLog.debug("aft wc 1")
        }
    }

    whatHeCanSayMethod.hook {
        before {
            SLog.debug("bef wc 2")
        }

        after {
            SLog.debug("aft wc 2")
        }
    }

    whatHeCanSayMethod.hook {
        before {
            SLog.debug("bef wc 3")
        }

        before {
            SLog.debug("bef wc 3 - dup") // 会覆盖上一个
        }

        after {
            SLog.debug("aft wc 3")
        }
    }

    // K RESULT：h h2 d l l2-l2 l d h2 h

    kobeMethod.hook(SakikoHookPriority.LOWEST) {
        before {
            SLog.debug("bef k l")
        }

        after {
            SLog.debug("aft k l")
        }
    }

    kobeMethod.hook {
        before {
            SLog.debug("bef k d")
        }

        after {
            SLog.debug("aft k d")
        }
    }

    kobeMethod.hook(SakikoHookPriority.LOWEST) {
        before {
            SLog.debug("bef k l2")
        }

        after {
            SLog.debug("aft k l2")
        }
    }

    kobeMethod.hook(SakikoHookPriority.HIGHEST) {
        before {
            SLog.debug("bef k h")
        }

        after {
            SLog.debug("aft k h")
        }
    }

    kobeMethod.hook(SakikoHookPriority.HIGHEST) {
        before {
            SLog.debug("bef k h2")
        }

        after {
            SLog.debug("aft k h2")
        }
    }

    val man = Man()
    man.whatHeCanSay()
    man.kobe()
}