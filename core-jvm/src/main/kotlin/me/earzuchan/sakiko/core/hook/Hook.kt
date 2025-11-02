package me.earzuchan.sakiko.core.hook

import me.earzuchan.sakiko.api.hook.HookParam
import me.earzuchan.sakiko.core.SakiBridgeImpl.shouldInvokeOrigin
import me.earzuchan.sakiko.core.utils.MambaUtils.proInvoke
import java.lang.reflect.Member

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

        // TIPS：已证明改参有用

        // 在本地调用
        return member.proInvoke(instance, *args)
    }
}