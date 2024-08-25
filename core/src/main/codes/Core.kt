package me.earzuchan.sakiko.core

import me.earzuchan.sakiko.api.HookConfig
import me.earzuchan.sakiko.api.SakikoModule
import me.earzuchan.sakiko.api.SakikoBridge
import me.earzuchan.sakiko.api.reflecting.constructor
import me.earzuchan.sakiko.api.utils.Log
import java.lang.reflect.Executable

const val TAG = "SakikoCore"

// Karlatemp圣千古
object Native {
    fun initNative(): Boolean = true
}

object Runner {
    private val globalContext = SakikoModule.ModuleContext()
    private val modules = mutableListOf<SakikoModule>()

    fun loadModule(moduleClass: Class<out SakikoModule>) {
        Log.debug(TAG, "Loading Module ${moduleClass.name}")

        val module: SakikoModule = moduleClass.constructor().it.newInstance(globalContext) as SakikoModule

        modules.add(module)

        module.onHook()
    }

    init {
        ensureNative()
        ensureBridge()
    }

    private fun ensureNative() {
        if (!Native.initNative()) {
            throw UnsatisfiedLinkError("Native library status is not OK")
        }

        Log.debug(TAG, "Native library loaded")
    }

    private fun ensureBridge() {
        // 通过反射设置INSTANCE以绕过Exception抛出
        SakikoBridge.INSTANCE = SakikoBridgeImpl()
        Log.debug(TAG, "Bridge initialized")
    }
}

public class SakikoBridgeImpl : SakikoBridge() {
    override fun hook(executable: Executable, config: HookConfig) {
        Log.debug(TAG, "Hooking ${executable.name}")
    }

    override fun unhook(executable: Executable) {
        Log.debug(TAG, "Unhooking ${executable.name}")
    }
}