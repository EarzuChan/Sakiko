plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    val inclu = project.projectDir.toString().replace('\\', '/') + "/include"

    mapOf(
        androidNativeArm32() to "armeabi-v7a",
        androidNativeArm64() to "arm64-v8a",
        androidNativeX64() to "x86_64",
    ).forEach { (target, _) ->
        // TODO：摸了，先不自己接入LSPlant，因为K/N不支持直接绑定C++
        /*target.compilations.getByName("main") {
            cinterops {
                val liblsplant by creating {
                    header("${inclu}/lsplant.hpp")
                }
            }
        }*/

        target.binaries {
            sharedLib {
                baseName = "sakiko"
            }
        }
    }
}