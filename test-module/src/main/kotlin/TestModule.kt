package me.earzuchan.sakiko.test

import com.highcapable.kavaref.KavaRef.Companion.resolve
import me.earzuchan.sakiko.api.annotations.ExposedSakikoModule
import me.earzuchan.sakiko.api.hook.hook
import me.earzuchan.sakiko.api.module.SakikoModule
import me.earzuchan.sakiko.api.module.encase
import me.earzuchan.sakiko.api.utils.SLog

private const val TAG = "TestModule"

// CHECK：其实也不必要全抄，有自己的一定特色挺好的，方便你我他？

@ExposedSakikoModule
class TestModule : SakikoModule {
    override fun onHook() = encase {
        SLog.info("onHook", TAG)

        SLog.info("Hook method2", TAG)

        "MethodsForTest".toClass().resolve().firstMethod {
            name = "method2"
        }.hook {
            replaceTo(1919810)
        }
    }
}
