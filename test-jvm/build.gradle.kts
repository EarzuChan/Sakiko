plugins { alias(libs.plugins.kotlinJvm) }

kotlin { jvmToolchain(21) }

dependencies { implementation(project(":test")) }

tasks.register<JavaExec>("perform") {
    group = "verification"

    dependsOn(":core-jvm:pack", ":launcher-jvm:pack", ":test-module:jar")

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("TestKt")

    doFirst {
        val coreJar = project(":core-jvm").tasks.named<Jar>("pack").get().archiveFile.get().asFile.absolutePath

        environment("SAKICORE", coreJar)

        val launcherJar = project(":launcher-jvm").tasks.named<Jar>("pack").get().archiveFile.get().asFile.absolutePath

        val testModuleJar = project(":test-module").tasks.jar.get().archiveFile.get().asFile.absolutePath

        val myArgs = listOf("-noverify", "-javaagent:$launcherJar=$testModuleJar")

        println("[INFO] SakikoGradle > Jvm 参数：${myArgs.joinToString("，")}}")
        jvmArgs(myArgs)
    }
}
