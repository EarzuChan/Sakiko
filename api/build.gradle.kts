import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier

plugins {
    alias(libs.plugins.kotlinJvm)
    `java-library`
    `maven-publish`
}

val appId = "${group}.api"

kotlin { jvmToolchain(21) }

tasks.compileKotlin { dependsOn(generateBuildConstants) }

val generatedCodePath = "src/main/generated"

val generateBuildConstants by tasks.registering {
    val outputDir = file(generatedCodePath)
    val constClzName = "BuildConstants"

    outputs.dir(outputDir)

    doLast {
        // 创建 Kotlin 文件
        val buildConfigClass = TypeSpec.objectBuilder(constClzName).addModifiers(KModifier.INTERNAL)
            .addProperty(
                PropertySpec.builder("VERSION", String::class, KModifier.CONST)
                    .initializer("%S", project.version)
                    .build()
            ).build()

        // 生成文件
        val file = FileSpec.builder(appId, constClzName)
            .addType(buildConfigClass)
            .build()

        outputDir.mkdirs()
        file.writeTo(outputDir)
    }
}

dependencies {
    api(libs.kavaRefCore)
    api(libs.kavaRefExt)
}

sourceSets { main { kotlin { srcDirs(generatedCodePath) } } }

publishing {
    publications {
        create<MavenPublication>("api") {
            artifact(tasks.jar)

            pom {
                name.set("Sakiko API")
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