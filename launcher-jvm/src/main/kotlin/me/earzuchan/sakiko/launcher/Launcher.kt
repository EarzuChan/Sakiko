package me.earzuchan.sakiko.launcher

import java.io.File
import java.lang.instrument.Instrumentation
import java.net.URL
import java.net.URLClassLoader

object LauncherEntry {
    @JvmStatic
    fun premain(agentArgs: String, inst: Instrumentation) = init(agentArgs)

    @JvmStatic
    fun agentmain(agentArgs: String, inst: Instrumentation) = init(agentArgs)

    private fun init(agentArgs: String) {
        val args = agentArgs.split(';')

        // 加载核心库
        val coreLibPath = System.getenv("SAKICORE") ?: error("没有设置核心库路径")

        val coreLibUrl = File(coreLibPath).apply {
            if (!exists()) error("核心库文件不存在")
        }.toURI().toURL()

        val coreClassLoader = CoreClassLoader(coreLibUrl)

        // 初始化core
        val coreClz = coreClassLoader.loadClass("me.earzuchan.sakiko.core.CoreKt")
        coreClz.getDeclaredMethod("initCore").invoke(null, true)

        // TODO：从参数加载模块，反射查找入口类（特定注解）并创建实例，环境准备好再执行模块实例的生命周期方法
    }
}


class CoreClassLoader(coreLibUrl: URL) : URLClassLoader(arrayOf(coreLibUrl), null)

class ModuleClassLoader(moduleLibUrl: URL, parent: ClassLoader) : URLClassLoader(arrayOf(moduleLibUrl), parent)