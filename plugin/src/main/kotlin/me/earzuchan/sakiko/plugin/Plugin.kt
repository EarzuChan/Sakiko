package me.earzuchan.sakiko.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.util.*

// TODO：WIP
class SakikoPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 注册任务
        project.tasks.register("buildDex", BuildDexTask::class.java)
    }
}

@DisableCachingByDefault(because = "Has side effects like copying files")
abstract class BuildDexTask : DefaultTask() {

    @TaskAction
    fun buildDex() {

    }
}

private fun Project.readLocalProperty(key: String, defaultValue: String? = null): String? {
    val properties = Properties()
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use {
        properties.load(it)
    }
    return properties.getProperty(key, defaultValue)
}