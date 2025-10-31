plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":test"))
}

tasks.register<JavaExec>("runJvmTest") {
    dependsOn(":launcher-jvm:launcherJar", ":test-module:jar")

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("test.jvm.TestMain")

    doFirst {
        val launcherJar = project(":launcher-jvm").tasks.named<Jar>("pack").get().archiveFile.get().asFile.absolutePath

        val testModuleJar = project(":test-module").tasks.jar.get().archiveFile.get().asFile.absolutePath

        val myArgs = listOf("-javaagent:$launcherJar=$testModuleJar")

        println("[INFO] SakikoGradle > Jvm args: ${myArgs.joinToString()}")
        jvmArgs(myArgs)
    }
}
