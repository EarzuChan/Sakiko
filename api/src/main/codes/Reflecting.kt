package me.earzuchan.sakiko.api.reflecting

import me.earzuchan.sakiko.api.HookConfig
import me.earzuchan.sakiko.api.UnhookConfig
import me.earzuchan.sakiko.api.hook
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method

fun Class<*>.method(configure: ExecutableMatchConfig.() -> Unit = {}): ExecutableMatches<Method> {
    val config = ExecutableMatchConfig().apply(configure)
    val methods = this.declaredMethods.filter { it.name == config.name }

    return if (config.paramsHasSet) {
        // 方法类型匹配
        ExecutableMatches(methods.filter { it.parameterTypes.contentEquals(config.params.toTypedArray()) })
    } else {
        ExecutableMatches(methods)
    }
}

fun Class<*>.constructor(configure: ExecutableMatchConfig.() -> Unit = {}): ExecutableMatches<Constructor<*>> {
    val config = ExecutableMatchConfig().apply(configure)
    val constructors = this.declaredConstructors

    return if (config.paramsHasSet) {
        // 构造器类型匹配
        ExecutableMatches(constructors.filter { it.parameterTypes.contentEquals(config.params.toTypedArray()) })
    } else {
        ExecutableMatches(constructors.toList())
    }
}

// 可执行匹配集
class ExecutableMatches<T : Executable>(private val executables: List<T>) {
    // 选择仅一个方法
    fun hook(configure: HookConfig.() -> Unit): UnhookConfig {
        if (executables.size == 1) {
            return executables[0].hook(configure)
        } else {
            throw IllegalStateException("Multiple methods found. Use all() to select specific methods.")
        }
    }

    // 选择所有方法
    fun all(): List<Executable> {
        return executables
    }

    val it: T
        get() {
            if (executables.size == 1) {
                return executables[0]
            } else {
                throw IllegalStateException("Multiple methods found. Use all() to select specific methods.")
            }
        }
}

// 批量 hook
fun List<Executable>.hook(configure: HookConfig.() -> Unit): List<UnhookConfig> = map { it.hook(configure) }

// 可执行匹配配置
class ExecutableMatchConfig {
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