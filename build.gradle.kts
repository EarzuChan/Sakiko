plugins {
    id("com.github.johnrengelman.shadow") version "6.1.0" apply false
    id("io.github.karlatemp.publication-sign") version "1.1.0"
    id("org.jetbrains.kotlin.jvm")
}

allprojects {
    group = "me.earzuchan.sakiko"
    version = "0.11.4.514"

    repositories {
        mavenCentral()
    }

    tasks.withType<Jar>().configureEach {
        archiveBaseName.set("sakiko")
    }
}

tasks.register<Exec>("buildNativeOnMyPc") {
    commandLine("C:\\Program Files\\Git\\bin\\bash.exe", ".scripts\\build_on_win.sh")
}

kotlin {
    jvmToolchain(11)
}