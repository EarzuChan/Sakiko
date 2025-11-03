plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    compileOnly(project(":api"))
}

kotlin { jvmToolchain(21) }