@file:Suppress("NewApi")

package me.earzuchan.sakiko.api.hook

import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.resolver.ConstructorResolver
import com.highcapable.kavaref.resolver.MethodResolver
import com.highcapable.kavaref.resolver.base.MemberResolver
import me.earzuchan.sakiko.api.bridge.SakiBridge
import java.lang.reflect.Member

class HookHandle<TOKEN : Any>(val token: TOKEN) {
    fun remove() = SakiBridge.requireInstance().asResolver().firstMethod { name = "unhook" }.invoke(this)
}

// CHECK：访问性
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
        // TIPS：先调用replace再bef/aft怎么办？所以得检查

        replaceLambda = action
    }

    fun replaceUnit(action: HookParam.() -> Unit) {
        replaceLambda = action
    }

    fun replaceTo(value: Any?) {
        replaceAny { value }
    }

    fun check() {
        if (replaceLambda != null && (beforeLambda != null || afterLambda != null)) throw IllegalStateException("Cannot use 'replace' with 'before' or 'after' hooks.")
    }
}

abstract class HookParam {
    abstract val member: Member

    abstract val args: Array<Any?>

    abstract val instance: Any?

    abstract var result: Any?

    abstract var throwable: Throwable?

    // TIPS：在原版XP中无此方法，纯为Fanke所加
    abstract fun callOriginal(): Any?

    abstract fun invokeOriginal(vararg args: Any?): Any?
}

// TODO：添加HOOK的优先级

inline fun MemberResolver<*, *>.hook(configure: HookConfig.() -> Unit): HookHandle<out Any> = when (this) {
    is ConstructorResolver,
    is MethodResolver -> {
        val config = HookConfig().apply(configure)
        val man = self

        hookMember(man, config)
    }

    else -> error("This type [$this] not support to hook, supported are Constructors and Methods")
}

fun hookMember(man: Member, config: HookConfig): HookHandle<out Any> =
    SakiBridge.requireInstance().checkAndHook(man, config)