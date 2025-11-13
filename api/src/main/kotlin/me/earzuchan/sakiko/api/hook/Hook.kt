@file:Suppress("NewApi")

package me.earzuchan.sakiko.api.hook

import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.resolver.ConstructorResolver
import com.highcapable.kavaref.resolver.MethodResolver
import com.highcapable.kavaref.resolver.base.MemberResolver
import me.earzuchan.sakiko.api.bridge.SakiBridge
import java.lang.reflect.Member

enum class SakikoHookPriority {
    HIGHEST, DEFAULT, LOWEST
}

class HookHandle<TOKEN : Any>(val token: TOKEN) {
    fun remove() = SakiBridge.requireInstance().asResolver().firstMethod {
        name = "unhook"
        superclass()
    }.invoke(this)
}

// CHECK：访问性：应该阻止用户对Lambda的直接访问
class HookConfig {
    var beforeLambda: (HookParam.() -> Unit)? = null
    var afterLambda: (HookParam.() -> Unit)? = null
    var replaceLambda: (HookParam.() -> Any?)? = null

    fun before(action: HookParam.() -> Unit) {
        beforeLambda = action
    }

    fun after(action: HookParam.() -> Unit) {
        afterLambda = action
    }

    fun replaceAny(action: HookParam.() -> Any?) {
        replaceLambda = action
    }

    fun replaceUnit(action: HookParam.() -> Unit) {
        replaceLambda = action
    }

    fun replaceTo(value: Any?) = replaceAny { value }

    fun check() {
        if (replaceLambda != null && (beforeLambda != null || afterLambda != null)) error("不可把替换和执行前/后同时使用")
    }
}

abstract class HookParam(
    val member: Member,
    var instance: Any?,
    val args: Array<Any?>
) {
    internal var _result: Any? = null

    var result: Any?
        set(value) {
            _result = value
            earlyReturn = true
            _throwable = null // 设置结果时，清除异常 LSP逻辑
        }
        get() = _result

    internal var _throwable: Throwable? = null

    // CHECK：是否实现类如果在BEFORE中设置，应该EARLY RET
    var throwable: Throwable?
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
    fun callOriginal(): Any? = invokeOriginal(*args)

    abstract fun invokeOriginal(vararg neoArgs: Any?): Any?
}

// 暴露的API，扩展方法属于是
inline fun MemberResolver<*, *>.hook(
    priority: SakikoHookPriority = SakikoHookPriority.DEFAULT,
    configure: HookConfig.() -> Unit
): HookHandle<out Any> =
    when (this) {
        is ConstructorResolver,
        is MethodResolver -> {
            val config = HookConfig().apply(configure)
            val man = self

            hookMember(man, config, priority)
        }

        else -> error("不支持Hook$this，您只能Hook构造器或者一般方法")
    }

// 也是暴露的API
fun hookMember(
    man: Member,
    config: HookConfig,
    priority: SakikoHookPriority = SakikoHookPriority.DEFAULT
): HookHandle<out Any> =
    SakiBridge.requireInstance().checkAndHook(man, config, priority)

// CHECK：还需要一个Member.hook()吗？