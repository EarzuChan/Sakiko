plugins {
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = group as String
    compileSdk = 36

    defaultConfig {
        minSdk = 21

        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64") }
    }

    sourceSets { getByName("main") { jniLibs.srcDirs("src/main/jniLibs") } }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // buildFeatures { prefab = true }
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":api"))
    // implementation(libs.lsplant)
    implementation(libs.aliuHook)
}

// TODO：等恢复自己写后，打包
/*val buildAndCopyNativeLibs = tasks.register("buildAndCopyNativeLibs") {
    group = "build"
    description = "Build native libraries and copy them"

    val libName = "sakiko"
    val nativeProject = project(":native-android")

    dependsOn(nativeProject.tasks.named("linkReleaseSharedAndroidNativeArm32"))
    dependsOn(nativeProject.tasks.named("linkReleaseSharedAndroidNativeArm64"))
    dependsOn(nativeProject.tasks.named("linkReleaseSharedAndroidNativeX64"))

    doLast {
        val libJNILibsDir = project.layout.projectDirectory.dir("src/main/jniLibs").asFile

        val architectureMappings = mapOf(
            "androidNativeArm32" to "armeabi-v7a",
            "androidNativeArm64" to "arm64-v8a",
            "androidNativeX64" to "x86_64",
        )

        // 清理旧的 tetsu.so（只删这个文件）
        architectureMappings.values.forEach { jniArch ->
            val oldFilePath = "$jniArch/lib$libName.so"
            val libFile = File(libJNILibsDir, oldFilePath)

            if (libFile.exists()) {
                libFile.delete()
                println("已删除旧的：$oldFilePath")
            } else println("找不到旧的：$oldFilePath")
        }

        // 复制新的本机库文件
        architectureMappings.forEach { (buildArch, jniArch) ->
            val sourceFilePath = "build/bin/$buildArch/releaseShared/lib$libName.so"
            val sourceFile = nativeProject.file(sourceFilePath)

            if (!sourceFile.exists()) println("${sourceFilePath}不存在，${jniArch}的构建可能失败了")
            else {
                val targetDir = File(libJNILibsDir, jniArch)
                println("正在把${sourceFilePath}复制到$targetDir")
                targetDir.mkdirs()
                copy {
                    from(sourceFile)
                    into(targetDir)
                }
            }
        }

        // 删除 releaseShared 中间产物
        nativeProject.file("build/bin").deleteRecursively()
    }
}*/
