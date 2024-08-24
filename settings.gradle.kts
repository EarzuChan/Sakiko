pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.0.10"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "Sakiko"

include("api-old")
include("core-old")
include("extension-old")
include("loader-old")

include("api")
include("loader")
include("test")
include("core")

