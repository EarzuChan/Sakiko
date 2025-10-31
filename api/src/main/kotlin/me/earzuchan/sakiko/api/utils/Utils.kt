@file:Suppress("NewApi")

package me.earzuchan.sakiko.api.utils

import java.lang.reflect.Constructor
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier

object SLog {

    private const val TAG: String = "Sakiko"

    enum class Level {
        DEBUG, INFO, WARN, ERROR
    }

    private var currentLevel: Level = Level.DEBUG

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

// CHECK：我觉得没准下放到平台？各平台不同。还是取交集（兼容性）
object HookCheckUtils {
    fun check(man: Member) {
        require(man is Method || man is Constructor<*>) { "Only methods and constructors can be hooked: $man" }
        if (man is Method) require(!Modifier.isAbstract(man.modifiers)) { "Cannot hook abstract methods: $man" }

        // TODO：另外native暂不支持，以后支不支持

        val manClz = man.declaringClass

        // TODO：还有就是：Array and primitive types are not supported；Currently, native methods are not supported
        if (manClz.getClassLoader() === Runnable::class.java.getClassLoader()) {
            // TODO：检测应用（还是系统）类加载器：豁免的可以Hook，禁止的不给Hook
        } // TODO：检查框架类加载器和框架类的不给Hook
    }
}