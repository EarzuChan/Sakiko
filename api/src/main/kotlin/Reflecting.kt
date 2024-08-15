package me.earzuchan.sakiko.api.reflecting

import me.earzuchan.sakiko.api.HookConfig
import me.earzuchan.sakiko.api.UnhookConfig
import me.earzuchan.sakiko.api.hook
import java.lang.reflect.Method

fun Class<*>.method(configure: MethodMatchConfig.() -> Unit): MethodMatches {
    val config = MethodMatchConfig().apply(configure)
    val methods = this.declaredMethods.filter { it.name == config.name }

    return if (config.paramsHasSet) {
        // 方法类型匹配
        MethodMatches(methods.filter { it.parameterTypes.contentEquals(config.params.toTypedArray()) })
    } else {
        MethodMatches(methods)
    }
}

// 方法匹配器
class MethodMatches(private val methods: List<Method>) {
    // 选择仅一个方法
    fun hook(configure: HookConfig.() -> Unit): UnhookConfig {
        if (methods.size == 1) {
            return methods[0].hook(configure)
        } else {
            throw IllegalStateException("Multiple methods found. Use all() to select specific methods.")
        }
    }

    // 选择所有方法
    fun all(): List<Method> {
        return methods
    }
}

// 批量 hook
fun List<Method>.hook(configure: HookConfig.() -> Unit): List<UnhookConfig> = map { it.hook(configure) }

// 方法匹配配置
class MethodMatchConfig {
    lateinit var name: String
    val params = mutableListOf<Class<*>>()
    var paramsHasSet = false

    fun param(vararg givenParams: Class<*>) {
        if (paramsHasSet) {
            throw IllegalStateException("Params has been set")
        }
        params.addAll(givenParams)
        paramsHasSet = true
    }

    fun emptyParam() {
        param()
    }
}

// 简便化的类名匹配
fun String.toClass(): Class<*> {
    return Class.forName(this)
}

val StringClass = String::class.java
val IntType = Int::class.javaPrimitiveType
val LongType = Long::class.javaPrimitiveType
val FloatType = Float::class.javaPrimitiveType
val DoubleType = Double::class.javaPrimitiveType
val BooleanType = Boolean::class.javaPrimitiveType
val ByteType = Byte::class.javaPrimitiveType
val ShortType = Short::class.javaPrimitiveType
val CharType = Char::class.javaPrimitiveType
val UnitType = Unit::class.javaPrimitiveType