plugins {
    alias(libs.plugins.kotlinJvm)
    `java-library`
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":api"))
    implementation(libs.bytebuddy)
    implementation(libs.kotlinxCoroutinesCore)
}

tasks.withType<Jar> { from(sourceSets.main.get().resources) }

// TIPS：mac的库需要在mac上构建
val buildAndCopyNativeLibs by tasks.registering {
    group = "build"
    description = "Build native libraries and copy them"

    val nativeProject = project(":native-jvm")

    dependsOn(nativeProject.tasks.named("linkReleaseSharedMingwX64"))
    // dependsOn(nativeProject.tasks.named("linkReleaseSharedLinuxX64"))
    // dependsOn(nativeProject.tasks.named("linkReleaseSharedMacosArm64"))

    doLast {
        val libJNILibsDir = project.layout.projectDirectory.dir("src/main/resources/native").asFile

        val architectureMappings = mapOf(
            "mingwX64" to ("win-x64" to "sakiko.dll"),
            // "linuxX64" to ("linux-x64" to "libsakiko.so"),
            // "macosArm64" to ("mac-arm64" to "libsakiko.dylib"),
        )

        // 清理旧的 sakiko.so（只删这个文件）
        architectureMappings.values.forEach { v ->
            val (jniArch, libName) = v

            val oldFilePath = "$jniArch/$libName"
            val sakikoFile = File(libJNILibsDir, oldFilePath)

            if (sakikoFile.exists()) {
                sakikoFile.delete()
                println("已删除旧的：$oldFilePath")
            } else println("找不到旧的：$oldFilePath")
        }

        // 复制新的 so
        architectureMappings.forEach { (buildArch, v) ->
            val (jniArch, libName) = v

            val sourceFilePath = "build/bin/$buildArch/releaseShared/$libName"
            val sourceFile = nativeProject.file(sourceFilePath)

            if (!sourceFile.exists()) println("${sourceFilePath}不存在，${jniArch}的构建可能失败了；mac的库需要在mac上构建")
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
}