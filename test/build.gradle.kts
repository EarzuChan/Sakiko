plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(libs.junit)
}

kotlin { jvmToolchain(21) }
