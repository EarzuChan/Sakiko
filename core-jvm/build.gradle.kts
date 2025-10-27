plugins {
    alias(libs.plugins.kotlinJvm)
    `java-library`
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":api"))
    implementation(libs.bytebuddy)
}

/*val buildAndCopyNativeLibs = tasks.register("buildAndCopyNativeLibs") {
    group = "build"
    description = "Build native libraries and copy them"

    val nativeProject = project(":native")

    *//*dependsOn(nativeProject.tasks.named("linkReleaseSharedAndroidNativeArm32"))
    dependsOn(nativeProject.tasks.named("linkReleaseSharedAndroidNativeArm64"))
    dependsOn(nativeProject.tasks.named("linkReleaseSharedAndroidNativeX64"))*//*

    doLast {
        val libJNILibsDir = project.layout.projectDirectory.dir("src/main/jniLibs").asFile

        val architectureMappings = mapOf(
            *//*"androidNativeArm32" to "armeabi-v7a",
            "androidNativeArm64" to "arm64-v8a",
            "androidNativeX64" to "x86_64",*//*
        )

        // 清理旧的 tetsu.so（只删这个文件）
        architectureMappings.values.forEach { jniArch ->
            // val tetsuFile = File(libJNILibsDir, "$jniArch/libtetsu.so")
            // if (tetsuFile.exists()) tetsuFile.delete()
        }

        // 复制新的 so
        *//*architectureMappings.forEach { (buildArch, jniArch) ->
            val sourceFile = nativeProject.file("build/bin/$buildArch/releaseShared/libtetsu.so")

            if (!sourceFile.exists()) println("${sourceFile.path}不存在，${jniArch}的构建可能失败了")
            else {
                val targetDir = File(libJNILibsDir, jniArch)
                println("正在把${sourceFile.path}复制到$targetDir")
                targetDir.mkdirs()
                copy {
                    from(sourceFile)
                    into(targetDir)
                }
            }
        }*//*

        // 删除 releaseShared 中间产物
        nativeProject.file("build/bin").deleteRecursively()
    }
}*/

// TODO：打包JAR配置