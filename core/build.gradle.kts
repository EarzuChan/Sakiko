plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":api"))
}

kotlin {
    jvmToolchain(21)
}