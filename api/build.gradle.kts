plugins {
    kotlin("jvm")
}

val manCodePath = "src/main/codes"
val generatedCodePath = "src/main/generated"

val generateCodesTimely by tasks.registering {
    val outputDir = file(generatedCodePath)
    val versionFile = File(outputDir, "Version.kt")

    inputs.property("version", project.version)
    outputs.file(versionFile)

    doLast {
        outputDir.mkdirs()
        versionFile.writeText(
            """
            package me.earzuchan.sakiko.api

            const val VERSION = "${project.version}"
        """.trimIndent()
        )
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateCodesTimely)
}

dependencies {
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

tasks.jar {
    archiveClassifier.set("api")
}

sourceSets {
    main {
        kotlin {
            srcDirs(manCodePath, generatedCodePath)
        }
    }
}