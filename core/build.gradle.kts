plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":api"))
}

kotlin {
    jvmToolchain(21)
}

tasks.jar {
    archiveClassifier.set("core")
}

sourceSets {
    main {
        kotlin {
            srcDirs("src/main/codes")
        }
    }
}