plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.composeCompiler) apply false
}

buildscript { dependencies { classpath(libs.kotlinPoet) } }

allprojects {
    group = "me.earzuchan.sakiko"
    version = "0.0.1"
}

subprojects { tasks.withType<Jar> { archiveBaseName.set("sakiko-${project.name}") } }