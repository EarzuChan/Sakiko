import org.gradle.kotlin.dsl.dependencies

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    val inclu = project.projectDir.toString().replace('\\', '/') + "/include"

    // TIPS：mac的库需要在mac上构建
    mapOf(
        mingwX64() to "win-x64",
        linuxX64() to "linux-x64",
        macosArm64() to "mac-arm64",
    ).forEach { (target, archName) ->
        target.compilations.getByName("main") {
            cinterops {
                val archFix = if (archName == "win-x64") "win" else "unix"

                val libjava by creating {
                    header("$inclu/jvmti_$archFix.h")
                }
            }
        }

        target.binaries {
            sharedLib {
                baseName = "sakiko"
            }
        }
    }
}

dependencies {
    commonMainImplementation(libs.kotlinxCoroutinesCore)
}