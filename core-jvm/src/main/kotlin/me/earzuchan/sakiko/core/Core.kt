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

        // 获取或创建Hook实体
        val entity = hookRegistry.computeIfAbsent(hookId) { HookEntity(man) }

        // 第三步：添加回调（线程安全）
        synchronized(entity.callbacks) {
            require(entity.callbacks.none { it.config == config }) {
                "这个回调已经肘赢过：${man.name}"
            }

            val handle = HookHandle(TokenImpl(hookId, config))
            val entry = CallbackEntry(config, handle, priority)

            // 找第一个优先级小于当前优先级的位置（ordinal更大）
            val insertIndex = entity.callbacks.indexOfFirst { it.priority > priority }
                .takeIf { it >= 0 } ?: entity.callbacks.size
            entity.callbacks.add(insertIndex, entry)

            SLog.debug("客人到了：$man；ID：$hookId；Priority：$priority；CCB：${entity.callbacks.size}", TAG)

            return handle
        }
    }

    // ==================== 核心Unhook流程 ====================

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

                // 所有回调移除后，清理HookContext；processedMethodMap不清理（已实装的字节码无法还原）
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
    fun handleHookedMethodJvm(entity: HookEntity, instance: Any?, args: Array<Any?>): Any? {
        val TAG = "SBI_HandleHookedMethodJvm"

        val man = entity.member

        // 创建参数对象
        val param = HookParamImpl(man, instance, args)

        // 快照：避免迭代时被并发修改
        val snapshot = synchronized(entity.callbacks)  { entity.callbacks.map { it.config } }

        return handleHookedMethod(man, snapshot, param)
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
        return arrayOf(handleHookedMethodJvm(entity, if (isStatic) null else idThisArgs[1], args))
    }
}