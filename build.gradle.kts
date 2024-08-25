plugins {
    kotlin("jvm")
}

allprojects{
    group = "me.earzuchan.sakiko"
    version = "0.11.4.514"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    tasks.withType<Jar>().configureEach {
        archiveBaseName.set("sakiko")
    }

    kotlin {
        jvmToolchain(11)
    }
}

tasks.register<Exec>("buildNativeOnMyPc") {
    commandLine("C:\\Program Files\\Git\\bin\\bash.exe", ".scripts\\build_on_win.sh")
}