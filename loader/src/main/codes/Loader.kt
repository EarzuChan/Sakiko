package me.earzuchan.sakiko.loader

import me.earzuchan.sakiko.api.SakikoModule
import me.earzuchan.sakiko.api.reflecting.StringClass
import me.earzuchan.sakiko.api.reflecting.method
import me.earzuchan.sakiko.api.reflecting.toClass
import me.earzuchan.sakiko.core.Runner
import me.earzuchan.sakiko.api.utils.Log
import java.lang.instrument.Instrumentation

const val TAG = "SakikoLoader"

object LoaderEntry {
    @JvmStatic
    fun premain(agentArgs: String, inst: Instrumentation) {
        Log.info(TAG, "Premain")


    }
}