package me.earzuchan.sakiko.launcher

import java.lang.instrument.Instrumentation

object LauncherEntry {
    @JvmStatic
    fun premain(agentArgs: String, inst: Instrumentation) = init(agentArgs)

    @JvmStatic
    fun agentmain(agentArgs: String, inst: Instrumentation) = init(agentArgs)

    private fun init(agentArgs: String) {
        val args = agentArgs.split(';')


    }

    class CoreClassLoader : ClassLoader() {

    }
}