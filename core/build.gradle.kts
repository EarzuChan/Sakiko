plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":api"))
}

kotlin {
    jvmToolchain(11)
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