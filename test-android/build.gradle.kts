plugins {
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

val appId = "${group}.test"
val verName = version as String

android {
    namespace = appId
    compileSdk = 36

    defaultConfig {
        applicationId = appId
        minSdk = 21
        targetSdk = 36
        versionCode = verName.replace(".", "").toInt()
        versionName = verName

        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            packaging {
                resources {
                    excludes += listOf(
                        "kotlin/**",
                        "assets/**",
                        "DebugProbesKt.bin",
                        "kotlin-tooling-metadata.json"
                    )
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures { compose = true }
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(libs.coreKtx)
    implementation(libs.lifecycleRuntimeKtx)
    implementation(libs.activityCompose)

    implementation(platform(libs.composeBom))
    implementation(libs.composeMaterial3)
    implementation(libs.composeUi.toolingPreview)
    debugImplementation(libs.composeUi.tooling)

    implementation(project(":core-android"))
    implementation(project(":test"))
    implementation(project(":test-module"))
}