package me.earzuchan.sakiko.core.models

import me.earzuchan.sakiko.api.hook.HookConfig
import de.robv.android.xposed.XC_MethodHook

data class TokenImpl(val unhook: XC_MethodHook.Unhook)