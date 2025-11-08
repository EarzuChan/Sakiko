plugins {
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.androidLibrary)
}

dependencies {
    implementation(project(":core-android")) // 暂不采用动态加载Dex
    implementation(libs.dexKit)
}

kotlin { jvmToolchain(21) }

val appId = "$group.launcher"

android {
    namespace = appId
    compileSdk = 36

    defaultConfig {
        minSdk = 21

        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}