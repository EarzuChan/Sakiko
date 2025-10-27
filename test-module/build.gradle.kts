plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    compileOnly(project(":api"))
    compileOnly(project(":test"))
}

kotlin { jvmToolchain(21) }