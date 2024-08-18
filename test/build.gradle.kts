plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":api"))
}

kotlin {
    jvmToolchain(21)
}

sourceSets {
    main {
        kotlin {
            srcDirs("src/main/codes")
        }
    }
    test {
        kotlin {
            srcDirs("src/test/codes")
        }
    }
}