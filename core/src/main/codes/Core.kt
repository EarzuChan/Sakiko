package me.earzuchan.sakiko.core

import me.earzuchan.sakiko.api.HookConfig
import me.earzuchan.sakiko.api.SakikoModule
import me.earzuchan.sakiko.api.SakikoBridge
import me.earzuchan.sakiko.api.reflecting.constructor
import me.earzuchan.sakiko.core.util.Log
import java.lang.reflect.Executable

const val TAG = "SakikoCore"

// Karlatemp圣千古
object Native {
    fun initNative(): Boolean = true
}

object Runner {
    val globalContext = SakikoModule.ModuleContext()

    fun loadModule(moduleClass: Class<out SakikoModule>) {
        val module: SakikoModule = moduleClass.constructor().it.newInstance(globalContext) as SakikoModule
        module.onHook()
    }

    init {
        Log.setLevel(Log.Level.DEBUG)

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
        Log.debug(TAG, "Hook ${executable.name}")
    }

    override fun unhook(executable: Executable) {
        Log.debug(TAG, "Unhook ${executable.name}")
    }
}