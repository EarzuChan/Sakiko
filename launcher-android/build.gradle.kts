plugins {
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.androidLibrary)
    `maven-publish`
}

dependencies {
    implementation(project(":core-android")) // 暂不采用动态加载核心
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

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("launcher-android") {
                from(components["release"])

                pom {
                    name.set("Sakiko Launcher for Android")
                    url.set("https://github.com/EarzuChan/Sakiko")

                    developers {
                        developer {
                            name.set("Earzu Chan")
                            email.set("huascq@gmail.com")
                        }
                    }

                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://www.opensource.org/licenses/MIT")
                        }
                    }
                }
            }
        }
    }
}