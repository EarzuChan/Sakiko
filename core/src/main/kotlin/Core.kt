package me.earzuchan.sakiko.core

import me.earzuchan.sakiko.api.HookConfig
import me.earzuchan.sakiko.api.SakikoBaseModule
import me.earzuchan.sakiko.api.SakikoBridge
import me.earzuchan.sakiko.core.util.Log
import java.lang.reflect.Method

const val TAG = "SakikoCore"

// Karlatemp圣千古
object KarlatempCompat {
    fun initNative(): Boolean = true
}

object Runner {
    fun loadModule(module: SakikoBaseModule) {
        module.onHook()
    }

    init {
        Log.setLevel(Log.Level.DEBUG)

        ensureNative()
        ensureBridge()
    }

    private fun ensureNative() {
        if (!KarlatempCompat.initNative()) {
            throw UnsatisfiedLinkError("Native library status is not OK")
        }

        Log.debug(TAG, "Native library loaded")
    }

    private fun ensureBridge() {
        // 通过反射设置INSTANCE以绕过Exception抛出
        SakikoBridge.INSTANCE = SakikoKarlatempBridge()
        Log.debug(TAG, "Bridge initialized")
    }
}

public class SakikoKarlatempBridge : SakikoBridge() {
    override fun hook(method: Method, config: HookConfig) {
        Log.debug(TAG, "Hook ${method.name}")
    }

    override fun unhook(method: Method) {
        Log.debug(TAG, "Unhook ${method.name}")
    }
}