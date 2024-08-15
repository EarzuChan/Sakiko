package me.earzuchan.sakiko.core

import me.earzuchan.sakiko.api.HookConfig
import me.earzuchan.sakiko.api.SakikoBaseModule
import me.earzuchan.sakiko.api.SakikoBridge
import me.earzuchan.sakiko.core.util.Log
import java.lang.reflect.Method

const val TAG = "SakikoCore"

object Runner {
    fun runModule(module: SakikoBaseModule) {
        module.onHook()
    }

    init {
        Log.setLevel(Log.Level.DEBUG)

        ensureNative()
        ensureBridge()
    }

    private external fun helloMamba(): Boolean

    private fun ensureNative() {
        if (!helloMamba()) {
            throw UnsatisfiedLinkError("Native library status is not OK")
        }

        Log.debug(TAG, "Native library loaded")
    }

    private fun ensureBridge() {
        SakikoBridge.INSTANCE = SakikoBridgeImpl()
        Log.debug(TAG, "Bridge initialized")
    }
}

public class SakikoBridgeImpl : SakikoBridge() {
    override fun hook(method: Method, config: HookConfig) {
        Log.debug(TAG, "Hook ${method.name}")
    }

    override fun unhook(method: Method) {
        Log.debug(TAG, "Unhook ${method.name}")
    }
}