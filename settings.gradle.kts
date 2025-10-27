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
    }
}

include(":api")

include(":core-jvm")
include(":native-jvm")
include(":launcher-jvm")

include(":core-android")
include(":native-android")

include(":test")
include(":test-module")
include(":test-jvm")
include(":test-android")

rootProject.name = "Sakiko"