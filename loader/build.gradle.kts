plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
}

kotlin {
    jvmToolchain(21)
}

tasks.register<Jar>("agentJar") {
    archiveClassifier.set("loader")
    manifest {
        attributes["Premain-Class"] = "me.earzuchan.sakiko.loader.LoaderEntry"
    }
    from(sourceSets.main.get().output)
}

tasks.register<JavaExec>("testLoader") {
    // dependsOn(":buildNativeOnMyPc")

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("me.earzuchan.sakiko.loader.LoaderEntry")

    doFirst {
        // val nativeLibDir = file("${project.rootDir}/native-old/build")
        // val nativeLib = nativeLibDir.listFiles()?.firstOrNull { it.extension == "dll" || it.extension == "so" }
        val agentJar = tasks.named<Jar>("agentJar").get().archiveFile.get().asFile

        /*if (nativeLib != null) {
            println("找到本机库: ${nativeLib.absolutePath}")*/
        val myArgs = arrayOf(/*"-agentpath:${nativeLib.absolutePath}",*/ "-javaagent:${agentJar.absolutePath}")
        println("JVM参数: ${myArgs.joinToString { it }}")
        jvmArgs(*myArgs)
        /*} else {
            throw GradleException("没找到位于${nativeLibDir}的本机库")
        }*/
    }
}

sourceSets {
    main {
        kotlin {
            srcDirs("src/main/codes")
        }
    }
}