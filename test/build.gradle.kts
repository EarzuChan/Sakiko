plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":api"))
}

kotlin {
    jvmToolchain(11)
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