plugins {
    kotlin("jvm")
}
dependencies {
    implementation(kotlin("stdlib-jdk8"))

}
repositories {
    mavenCentral()
}
kotlin {
    jvmToolchain(21)
}
tasks.jar {
    archiveClassifier.set("api")
}