plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    mapOf(
        mingwX64() to "win-x64",
        linuxX64() to "linux-x64",
        macosArm64() to "mac-arm64",
    ).forEach { (target, _) ->
        target.binaries {
            sharedLib {
                baseName = "sakiko"
            }
        }
    }

    /*sourceSets {
        commonMain {}

        nativeMain {dependsOn(commonMain.get())}

        arrayOf(mingwX64Main, linuxX64Main, macosArm64Main).forEach {
            it.get().dependsOn(nativeMain.get())
        }
    }*/
}