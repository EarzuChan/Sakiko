tasks.register<Jar>("packCore") {
    archiveBaseName.set("sakiko-old")

    /*configurations = listOf(
        project(":core-old").configurations.runtimeClasspath.get(),
        project(":extension-old").configurations.runtimeClasspath.get()
    )*/

    from(project(":core-old").sourceSets.main.get().output)
    from(project(":extension-old").sourceSets.main.get().output)

    archiveClassifier.set("core")
}

tasks.register<Jar>("packAll") {
    dependsOn(":loader-old:copyApi")

    archiveBaseName.set("sakiko-old")

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    archiveClassifier.set("loader")

    from(files(tasks.named("packCore").get().outputs)) {
        into("io/github/karlatemp/jvmhook/launcher")
        rename { "classes.jar" }
    }

    manifest {
        attributes(
            "Premain-Class" to "io.github.karlatemp.jvmhook.launcher.Launcher"
        )
    }
}

tasks.register<Copy>("copyApi") {
    into(File(buildDir, "libs"))
    from(project(":api-old").tasks.named("jar").get().outputs.files)
}

dependencies {
    compileOnly("org.jetbrains:annotations:20.1.0")
}