plugins {
    id("java")
    id("java-library")
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