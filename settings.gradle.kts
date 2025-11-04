pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Use Maven Central as the default repository (where Gradle will download dependencies) in all subprojects.
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
        maven("https://maven.aliucord.com/releases")
    }
}

include(":api")

include(":core-jvm")
include(":native-jvm")
include(":launcher-jvm")
include(":test-jvm")

include(":core-android")
include(":native-android")
include(":launcher-android")
include(":test-android")

include(":test")
include(":test-module")

rootProject.name = "Sakiko"