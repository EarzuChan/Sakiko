package me.earzuchan.sakiko.launcher

import android.util.Log
// import me.earzuchan.sakiko.core.initCore

object Launcher {
    private const val TAG = "Launcher"

    private var inited = false

    fun init() {
        if (!inited) {
            // initCore()
            inited = true
            Log.i(TAG, "初始化完成")
        }
    }

    fun <T> ensureInit(block: () -> T) {
        init()
        block()
    }

    // 给一个Dex的路径，弄进cl，然后自动查找和加载里面的模块
    fun findAndLoadFromDexByPath(dexPath: String) = ensureInit {
        TODO()
    }

    // 给一个ClassLoader，自动查找和加载里面的模块
    fun findAndLoadModuleFromClassLoader(classLoader: ClassLoader) = ensureInit {
        TODO()
    }


    // 别人自家的cl自家的class我加载上
    fun loadModuleFromClass(clazz: Class<*>) = ensureInit {
        TODO()
    }
}