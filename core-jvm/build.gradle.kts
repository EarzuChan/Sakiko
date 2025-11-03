import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.shadowJar)
    alias(libs.plugins.kotlinJvm)
    `java-library`
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":api"))
    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.asm)
    implementation(libs.asmUtil)
}

tasks.jar { enabled = false }

tasks.register<ShadowJar>("pack") {
    group = "build"

    // 打包项目主源集
    from(sourceSets.main.get().output)

    // 打包依赖项
    configurations = listOf(project.configurations.runtimeClasspath.get())
}

// TIPS：mac的库需要在mac上构建
val buildAndCopyNativeLibs by tasks.registering {
    group = "build"
    description = "Build native libraries and copy them"

    val libName = "sakiko"
    val nativeProject = project(":native-jvm")

    dependsOn(nativeProject.tasks.named("linkReleaseSharedMingwX64"))
    dependsOn(nativeProject.tasks.named("linkReleaseSharedLinuxX64"))
    dependsOn(nativeProject.tasks.named("linkReleaseSharedMacosArm64"))

    doLast {
        val libJNILibsDir = project.layout.projectDirectory.dir("src/main/resources/native").asFile

        val architectureMappings = mapOf(
            "mingwX64" to ("win-x64" to "$libName.dll"),
            "linuxX64" to ("linux-x64" to "lib$libName.so"),
            "macosArm64" to ("mac-arm64" to "lib$libName.dylib"),
        )

        // 清理旧的本机库文件
        architectureMappings.values.forEach { v ->
            val (jniArch, platformLibName) = v

            val oldFilePath = "$jniArch/$platformLibName"
            val libFile = File(libJNILibsDir, oldFilePath)

            if (libFile.exists()) {
                libFile.delete()
                println("已删除旧的：$oldFilePath")
            } else println("找不到旧的：$oldFilePath")
        }

        // 复制新的本机库文件
        architectureMappings.forEach { (buildArch, kv) ->
            val (jniArch, platformLibName) = kv

            val sourceFilePath = "build/bin/$buildArch/releaseShared/$platformLibName"
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