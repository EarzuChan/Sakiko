plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    mapOf(
        androidNativeArm32() to "armeabi-v7a",
        androidNativeArm64() to "arm64-v8a",
        androidNativeX64() to "x86_64",
    ).forEach { (target, _) ->
        target.binaries {
            sharedLib {
                baseName = "sakiko"
            }
        }
    }
}