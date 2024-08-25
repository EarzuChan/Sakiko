dependencies {
    api(project(":api"))
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