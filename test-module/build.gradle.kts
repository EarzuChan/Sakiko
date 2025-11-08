import java.util.Properties

plugins { alias(libs.plugins.kotlinJvm) }

dependencies { compileOnly(project(":api")) }

kotlin { jvmToolchain(21) }

// IMPROVE：太糙了
tasks.register<Exec>("packDex") {
    dependsOn("build")

    val androidHome = System.getenv("ANDROID_HOME") ?: readLocalProperty("sdk.dir") ?: error("找不到安卓SDK")

    val buildToolsVersion = "36.0.0"
    val d8ToolName = if (org.gradle.internal.os.OperatingSystem.current().isWindows) "d8.bat" else "d8"
    val d8Tool = "$androidHome/build-tools/$buildToolsVersion/$d8ToolName"
    println("[INFO] SakikoGradle > 啊一个D8应犹在：$d8Tool")

    val fileName = "sakiko-${project.name}-$version"
    val inputJar = file("build/libs/$fileName.jar")
    val outputDir = file("build/dexes")
    outputDir.deleteRecursively()
    outputDir.mkdirs()

    commandLine(d8Tool, "--release", "--min-api", "21", "--output", outputDir, inputJar)

    doLast {
        println("[INFO] SakikoGradle > 啊一个DEX构建完毕：$outputDir")

        val classesDex = outputDir.resolve("classes.dex")
        if (!classesDex.exists()) error("classes.dex未发现：${classesDex.absolutePath}")

        val renamedDex = outputDir.resolve("$fileName.dex")
        classesDex.renameTo(renamedDex)

        val targetDex = project(":test-android").file("src/main/assets/module.dex")

        targetDex.parentFile?.mkdirs()
        renamedDex.copyTo(targetDex, overwrite = true)

        println("[INFO] SakikoGradle > DEX已重命名并复制到：$targetDex")
    }
}

private fun readLocalProperty(key: String, defaultValue: String? = null): String? {
    val properties = Properties()
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use {
        properties.load(it)
    }
    return properties.getProperty(key, defaultValue)
}