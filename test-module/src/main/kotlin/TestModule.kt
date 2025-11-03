package me.earzuchan.sakiko.test

import com.highcapable.kavaref.KavaRef.Companion.resolve
import me.earzuchan.sakiko.api.annotations.ExposedSakikoModuleEntry
import me.earzuchan.sakiko.api.hook.hook
import me.earzuchan.sakiko.api.module.SakikoModuleEntry
import me.earzuchan.sakiko.api.module.encase
import me.earzuchan.sakiko.api.utils.SLog

private const val TAG = "TestModule"

@ExposedSakikoModuleEntry
class TestModuleEntry1 : SakikoModuleEntry {
    override fun onHook() = encase {
        SLog.info("示例模块入口1；Hook StaticMethods", TAG)

        val clz = "me.earzuchan.sakiko.test.StaticMethods".toClass().resolve()

        SLog.info("Hook method1", TAG)
        clz.firstMethod {
            name = "method1"
        }.hook { replaceTo(1919810) }

        SLog.info("Hook method2", TAG)
        clz.firstMethod {
            name = "method2"
        }.hook { replaceTo("恩情") }
    }
}

@ExposedSakikoModuleEntry
class TestModuleEntry2 : SakikoModuleEntry {
    override fun onHook() = encase {
        SLog.info("示例模块入口2；Hook InstanceMethods 和 Ctor", TAG)

        val clz = "me.earzuchan.sakiko.test.InstanceMethods".toClass().resolve()

        SLog.info("Hook method1", TAG)
        clz.firstMethod {
            name = "method1"
        }.hook { replaceTo(142857) }

        SLog.info("Hook method2", TAG)
        clz.firstMethod {
            name = "method2"
        }.hook { replaceTo("南下") }

        "me.earzuchan.sakiko.test.Ctor".toClass().resolve().firstConstructor().hook {
            before { args[0] = "我的醋" }
        }
    }
}
