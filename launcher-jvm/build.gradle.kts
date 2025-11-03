import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.shadowJar)
    alias(libs.plugins.kotlinJvm)
}
dependencies {
    // implementation(project(":core-jvm")) 应该独立加载以减少对应用程序类路径的干扰
    // CHECK：要不要引入
}

kotlin { jvmToolchain(21) }

tasks.jar { enabled = false }

tasks.register<ShadowJar>("pack") {
    group = "build"

    // 1. 告诉任务，把项目主源码集编译后的 class 文件打包进去
    from(sourceSets.main.get().output)

    // 2. 告诉任务，需要打包哪些依赖项。
    configurations = listOf(project.configurations.runtimeClasspath.get())

    manifest {
        attributes["Premain-Class"] = "me.earzuchan.sakiko.launcher.LauncherEntry"
        attributes["Agent-Class"] = "me.earzuchan.sakiko.launcher.LauncherEntry"
    }
}