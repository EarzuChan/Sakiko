package me.earzuchan.sakiko.loader

import me.earzuchan.sakiko.api.SakikoBaseModule
import me.earzuchan.sakiko.api.hook
import me.earzuchan.sakiko.api.reflecting.method
import me.earzuchan.sakiko.api.reflecting.toClass
import me.earzuchan.sakiko.core.Runner
import me.earzuchan.sakiko.core.util.Log
import java.lang.instrument.Instrumentation

const val TAG = "SakikoLoader"

object LoaderEntry {
    @JvmStatic
    fun premain(agentArgs: String?, inst: Instrumentation) {
        Log.info(TAG, "Premain")

        with(Runner) {
            runModule(object : SakikoBaseModule() {
                override fun onHook() {
                    "me.earzuchan.sakiko.loader.LoaderEntry".toClass().method {
                        name = "test"
                        param(String::class.java)
                    }.hook {
                        before {
                            println("Before hook")
                        }
                        after {
                            println("After hook")
                        }
                    }
                }
            })
        }
    }

    @JvmStatic
    fun main(args: Array<String>?) {
        Log.info(TAG, "Main")

        Log.info(TAG, "Test: ${test("baby")}")
    }

    @JvmStatic
    fun test(man: String): String {
        return "test $man"
    }
}