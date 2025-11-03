package me.earzuchan.sakiko.launcher

import io.github.classgraph.ClassGraph
import java.io.File
import java.lang.instrument.Instrumentation
import java.net.URL
import java.net.URLClassLoader

private fun logi(tag: String, msg: String) = println("[INFO] $tag > $msg")
private fun logd(tag: String, msg: String) = println("[DEBUG] $tag > $msg")
private fun logw(tag: String, msg: String) = println("[WARN] $tag > $msg")
private fun loge(tag: String, msg: String) = println("[ERROR] $tag > $msg")

object LauncherEntry {
    private const val TAG = "LauncherEntry"

    private val modules = mutableListOf<Any>()

    @JvmStatic
    fun premain(agentArgs: String, inst: Instrumentation) = init(agentArgs.split(';'))

    @JvmStatic
    fun agentmain(agentArgs: String, inst: Instrumentation) = init(agentArgs.split(';'))

    fun init(args: List<String>) {
        // 取得AppClassLoader
        val appClassLoader = ClassLoader.getSystemClassLoader()
        // val clz1 = appClassLoader.loadClass("me.earzuchan.sakiko.test.StaticMethods")
        // logi(TAG, "成功获得AppClassLoader：$appClassLoader")

        // 加载核心库
        val coreLibPath = System.getenv("SAKICORE") ?: error("没有设置核心库路径")

        val coreLibUrl = File(coreLibPath).apply {
            if (!exists()) error("核心库文件不存在：$absolutePath")
        }.toURI().toURL()
        logi(TAG, "成功找到核心库")

        // 加载并初始化core
        val coreClassLoader = CoreClassLoader(coreLibUrl)
        val coreClz = coreClassLoader.loadClass("me.earzuchan.sakiko.core.CoreKt")
        coreClz.getDeclaredMethod("initCore", Boolean::class.java).invoke(null, true)
        logi(TAG, "成功加载核心库")

        val sclClz = coreClassLoader.loadClass("me.earzuchan.sakiko.api.module.ModuleKt")
        val scClz = coreClassLoader.loadClass("me.earzuchan.sakiko.api.module.SakikoContext")
        val sakiCtxLocal = sclClz.getDeclaredField("sakiCtxLocal").apply {
            isAccessible = true
        }.get(null) as ThreadLocal<Any>
        sakiCtxLocal.set(scClz.getConstructor(ClassLoader::class.java).newInstance(appClassLoader))
        logi(TAG, "成功创建并注入Saki上下文")

        // 创建所有模块包的类加载器
        val moduleClassLoaders = args.mapNotNull { File(it).takeIf { f -> f.exists() } }.map {
            logi(TAG, "发现有效的模块包：${it.nameWithoutExtension}")
            it.toURI().toURL()
        }.map { ModuleClassLoader(it, coreClassLoader) }
        logi(TAG, "有效的模块包发现完成")

        // 发现所有的有效模块类
        val moduleClasses = moduleClassLoaders.flatMap { cl ->
            ClassGraph()
                .addClassLoader(cl)
                .enableClassInfo()
                .enableAnnotationInfo()
                .scan().getClassesWithAnnotation("me.earzuchan.sakiko.api.annotations.ExposedSakikoModuleEntry")
                .filter { classInfo ->
                    classInfo.implementsInterface("me.earzuchan.sakiko.api.module.SakikoModuleEntry") &&
                            !classInfo.isAbstract
                }.map {
                    val moduleName = it.name
                    logi(TAG, "发现有效的模块类：$moduleName")
                    cl.loadClass(moduleName)
                }
        }
        logi(TAG, "有效的模块类发现完成")

        // 实例化所有的有效模块类并初始化和添加，然后执行onHook
        moduleClasses.forEach { clz ->
            val onInitMethod = clz.getDeclaredMethod("onInit")
            val onHookMethod = clz.getDeclaredMethod("onHook")

            // 期望有且仅有一个构造器
            runCatching {
                clz.getConstructor().newInstance()
            }.onFailure {
                loge(TAG, "实例化${clz.name}失败\n${it.stackTraceToString()}")
            }.onSuccess { ins ->
                logi(TAG, "实例化${clz.name}成功")
                runCatching { onInitMethod.invoke(ins) }.onFailure { e ->
                    loge(TAG, "初始化${clz.name}失败\n${e.cause!!.stackTraceToString()}")
                }.onSuccess {
                    modules += ins
                    logi(TAG, "初始化${clz.name}成功")

                    // 确保Saki上下文已被提供
                    runCatching { onHookMethod.invoke(ins) }.onFailure { e ->
                        loge(TAG, "执行${clz.name}的onHook失败\n${e.cause!!.stackTraceToString()}")
                    }.onSuccess {
                        logi(TAG, "执行${clz.name}的onHook成功")
                    }
                }
            }
        }
    }
}

// TIPS：FOR TEST ONLY；记得添加`SAKICORE`环境变量
fun main(args: Array<String>) = LauncherEntry.init(args.toList())

class CoreClassLoader(coreLibUrl: URL) : URLClassLoader(arrayOf(coreLibUrl), null)

class ModuleClassLoader(moduleLibUrl: URL, parent: ClassLoader) : URLClassLoader(arrayOf(moduleLibUrl), parent)