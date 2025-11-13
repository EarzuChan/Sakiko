package me.earzuchan.sakiko.launcher

import android.util.Log
import dalvik.system.DexClassLoader
import me.earzuchan.sakiko.api.module.SakikoContext
import me.earzuchan.sakiko.api.module.SakikoModuleEntry
import me.earzuchan.sakiko.api.module.sakiCtxLocal
import me.earzuchan.sakiko.core.initCore
import org.luckypray.dexkit.DexKitBridge
import java.lang.Class

object Launcher {
    private const val TAG = "Launcher"

    private val modules = mutableListOf<Any>()

    private var inited = false

    private fun ClassLoader.setToApi() = sakiCtxLocal.set(SakikoContext(this))

    private val appClassLoader = Launcher::class.java.classLoader!!

    fun init() {
        if (!inited) {

            System.loadLibrary("dexkit")

            initCore()
            appClassLoader.setToApi()

            inited = true
            Log.i(TAG, "初始化完成")
        }
    }

    fun <T> ensureInit(block: () -> T) {
        init()
        block()
    }

    // 给一个Dex的路径，弄进cl，然后调用下面的
    fun findAndLoadModuleFromDexByPath(dexPath: String) = ensureInit {
        val classLoader = runCatching { DexClassLoader(dexPath, null, null, appClassLoader) }.onFailure {
            Log.e(TAG, "创建DEX类加载器时错误：${it.stackTraceToString()}")
        }.getOrThrow()
        findAndLoadModuleFromClassLoader(classLoader)
    }

    // 给一个ClassLoader，自动发现（使用DexKit，发现实现`SakikoModuleEntry`且有`@ExposedSakikoModuleEntry`的类），然后调用loadModuleEntry
    fun findAndLoadModuleFromClassLoader(classLoader: ClassLoader) = ensureInit {
        DexKitBridge.create(classLoader, true).use { bridge ->
            bridge.findClass {
                matcher {
                    interfaces { add("me.earzuchan.sakiko.api.module.SakikoModuleEntry") }
                    annotations { add { type = "me.earzuchan.sakiko.api.annotations.ExposedSakikoModuleEntry" } }
                }
            }.forEach { classData ->
                val moduleName = classData.name
                Log.i(TAG, "发现有效的模块类：$moduleName")
                loadModuleEntry(classLoader.loadClass(moduleName))
            }
        }
    }

    // 别人自家的cl自家的class我加载上
    fun loadModuleFromClass(clazz: Class<*>) = ensureInit { loadModuleEntry(clazz) }

    private fun loadModuleEntry(clz: Class<*>) {
        if (!SakikoModuleEntry::class.java.isAssignableFrom(clz)) error("类${clz.simpleName}不是一个一个SakikoModuleEntry")

        clz as Class<SakikoModuleEntry>

        // 期望有且仅有一个构造器
        runCatching {
            clz.getConstructor().newInstance()
        }.onFailure {
            Log.e(TAG, "实例化${clz.name}失败\n${it.stackTraceToString()}")
        }.onSuccess { ins ->
            Log.i(TAG, "实例化${clz.name}成功")
            runCatching { ins.onInit() }.onFailure { e ->
                Log.e(TAG, "初始化${clz.name}失败\n${e.cause!!.stackTraceToString()}")
            }.onSuccess {
                modules += ins
                Log.i(TAG, "初始化${clz.name}成功")

                // 确保Saki上下文已被提供
                runCatching { ins.onHook() }.onFailure { e ->
                    Log.e(TAG, "执行${clz.name}的onHook失败\n${e.cause!!.stackTraceToString()}")
                }.onSuccess {
                    Log.e(TAG, "执行${clz.name}的onHook成功")
                }
            }
        }
    }
}