plugins {
    alias(libs.plugins.kotlinJvm)
    `java-library`
}

kotlin { jvmToolchain(21) }

tasks.compileKotlin { dependsOn(generateCodesEachTime) }

val generatedCodePath = "src/main/generated"

val generateCodesEachTime by tasks.registering {
    val outputDir = file(generatedCodePath)
    val versionFile = File(outputDir, "Version.kt")

    inputs.property("version", project.version)
    outputs.file(versionFile)

    doLast {
        outputDir.mkdirs()
        versionFile.writeText(
            """package me.earzuchan.sakiko.api

internal const val VERSION = "${project.version}"""".trimIndent()
        )
    }
}

dependencies {
    api(libs.kavaRefCore)
    api(libs.kavaRefExt)
}

sourceSets { main { kotlin { srcDirs(generatedCodePath) } } }