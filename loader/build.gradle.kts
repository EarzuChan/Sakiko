plugins {
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
}

tasks.register<Jar>("agentJar") {
    archiveClassifier.set("loader")

    manifest {
        attributes["Premain-Class"] = "me.earzuchan.sakiko.loader.LoaderEntry"
    }

    from(sourceSets.main.get().output)
    from(project(":core").sourceSets.main.get().output)
}

sourceSets {
    main {
        kotlin {
            srcDirs("src/main/codes")
        }
    }
}