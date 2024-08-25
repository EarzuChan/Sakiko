package me.earzuchan.sakiko.test

import me.earzuchan.sakiko.api.SakikoModule
import me.earzuchan.sakiko.api.annotations.ExposedSakikoModule
import me.earzuchan.sakiko.api.reflecting.StringClass
import me.earzuchan.sakiko.api.reflecting.method
import me.earzuchan.sakiko.api.reflecting.toClass
import me.earzuchan.sakiko.api.utils.Log

@ExposedSakikoModule
class TestModule(moduleContext: ModuleContext) : SakikoModule(moduleContext) {
    override fun onHook() {
        Log.info("TestModule", "onHook")

        "me.earzuchan.sakiko.test.Main".toClass().method {
            name = "test"
            param(StringClass)
        }.hook {
            before {
                println("Before hook")
            }
            after {
                println("After hook")
            }
        }
    }
}