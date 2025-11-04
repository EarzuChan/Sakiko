import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// TIPS：这是一个JavaAgent

plugins {
    alias(libs.plugins.shadowJar)
    alias(libs.plugins.kotlinJvm)
}
dependencies {
    // implementation(project(":core-jvm")) 应该独立加载以减少对应用程序类路径的干扰
    // CHECK：要不要引入↓
    implementation(libs.classGraph)
}

kotlin { jvmToolchain(21) }

tasks.jar { enabled = false }

tasks.register<ShadowJar>("pack") {
    group = "build"

    // 打包主源集
    from(sourceSets.main.get().output)

    // 打包依赖项
    configurations = listOf(project.configurations.runtimeClasspath.get())

    manifest {
        attributes["Premain-Class"] = "me.earzuchan.sakiko.launcher.LauncherEntry"
        attributes["Agent-Class"] = "me.earzuchan.sakiko.launcher.LauncherEntry"
    }
}