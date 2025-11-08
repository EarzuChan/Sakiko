@file:Suppress("NewApi")

package me.earzuchan.sakiko.api.utils

import java.lang.reflect.Constructor
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier

object SLog {
    private const val TAG = "Sakiko"

    enum class Level {
        DEBUG, INFO, WARN, ERROR
    }

    private var currentLevel: Level = Level.DEBUG

    // CHECK：本API和YUKI不一样！
    fun setLevel(level: Level) {
        currentLevel = level
    }

    fun debug(message: String, tag: String = TAG) {
        if (currentLevel <= Level.DEBUG) println("[DEBUG] $tag > $message")
    }

    fun info(message: String, tag: String = TAG) {
        if (currentLevel <= Level.INFO) println("[INFO] $tag > $message")
    }

    fun warn(message: String, tag: String = TAG) {
        if (currentLevel <= Level.WARN) println("[WARN] $tag > $message")
    }

    fun error(message: String, tag: String = TAG) {
        if (currentLevel <= Level.ERROR) println("[ERROR] $tag > $message")
    }
}

object HookTargetValidateCheckUtils {
    private const val TAG = "HookTargetValidateCheckUtils"

    // JVM 平台不建议 Hook 的类型
    private val JVM_BLOCKED_CLASSES = setOf(
        Object::class.java,
        Class::class.java,
        String::class.java,
        ThreadLocal::class.java
    )

    // 值类型包装类
    private val PRIMITIVE_WRAPPER_CLASSES = setOf(
        java.lang.Boolean::class.java,
        java.lang.Byte::class.java,
        Character::class.java,
        java.lang.Short::class.java,
        Integer::class.java,
        java.lang.Long::class.java,
        java.lang.Float::class.java,
        java.lang.Double::class.java
    )

    fun check(man: Member) {
        require(man is Method || man is Constructor<*>) { "只能Hook方法或者构造器：$man" }
        if (man is Method) require(!Modifier.isAbstract(man.modifiers)) { "不可Hook抽象方法：$man" }

        // Native 方法检查
        check(!Modifier.isNative(man.modifiers)) { "暂不支持Hook原生方法" }

        val manClz = man.declaringClass

        // 不可 Hook Sakiko 自身方法
        check(!manClz.name.startsWith("me.earzuchan.sakiko.")) { "不可Hook Sakiko自身方法：$man" }

        // 数组类型和基本类型检查：因为JVM侧这样的Hook可能会导致不可预期的数组拷贝和装拆箱行为
        check(!manClz.isArray && !manClz.isPrimitive) { "不可Hook数组类型或基本类型的方法：$man" }

        // 关键类检查：这些类被JVM核心依赖，Hook了或会导致不可预期行为
        check(!JVM_BLOCKED_CLASSES.contains(manClz)) { "不可Hook某些关键类的方法：$man" }

        // 值类型包装类检查：因为JVM侧这样的Hook可能会导致不可预期的装拆箱行为
        check(!PRIMITIVE_WRAPPER_CLASSES.contains(manClz)) { "不可Hook值类型包装类的方法：$man" }

        // System.arraycopy 特殊检查
        check(manClz != System::class.java || man.name != "arraycopy") { "不可Hook System.arraycopy方法：$man" }
    }
}
