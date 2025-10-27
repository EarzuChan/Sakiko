import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.shadowJar)
    alias(libs.plugins.kotlinJvm)
}
dependencies {
    implementation(project(":core-jvm"))
}

kotlin { jvmToolchain(21) }

tasks.jar { enabled = false }

tasks.register<ShadowJar>("pack") {
    archiveClassifier.set("launcher-jvm")

    // 1. 告诉任务，把项目主源码集编译后的 class 文件打包进去
    from(sourceSets.main.get().output)

    // 2. 告诉任务，需要打包哪些依赖项。
    configurations = listOf(project.configurations.runtimeClasspath.get())

    manifest {
        attributes["Premain-Class"] = "me.earzuchan.sakiko.jvm.launcher.LauncherEntry"
        attributes["Can-Redefine-Classes"] = "true"
        attributes["Can-Retransform-Classes"] = "true"
    }
}