dependencies {
    api(project(":api"))
}

sourceSets {
    main {
        kotlin {
            srcDirs("src/main/codes")
        }
    }
    test {
        kotlin {
            srcDirs("src/test/codes")
        }
    }
}

tasks.register<Jar>("testModuleJar") {
    archiveClassifier.set("test-module")
    from(sourceSets.test.get().output)
}

tasks.register<JavaExec>("runTest") {
    dependsOn(":loader:agentJar")
    dependsOn("testModuleJar")
    // dependsOn(":buildNativeOnMyPc")

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("me.earzuchan.sakiko.test.Main")

    doFirst {
        val agentJar = project(":loader").tasks.named<Jar>("agentJar").get().archiveFile.get().asFile
        val testModuleJar = tasks.named<Jar>("testModuleJar").get().archiveFile.get().asFile
        val myArgs = arrayOf("-javaagent:${agentJar.absolutePath}=${testModuleJar.absolutePath}")
        println("JVM参数: ${myArgs.joinToString { it }}")
        jvmArgs(*myArgs)
    }
}