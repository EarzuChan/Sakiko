plugins {
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.androidLibrary)
}

dependencies {
    // implementation(project(":core-android")) 动态加载Dex
    // CHECK：要不要引入↓
    // implementation(libs.classGraph)
}

kotlin { jvmToolchain(21) }

val appId="$group.launcher"

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