dependencies {
    api("io.github.karlatemp:unsafe-accessor:1.7.0")
}

tasks.create<Jar>("sourcesJar") {
    dependsOn("classes")
    archiveClassifier.set("sources")
    from(project.sourceSets.main.get().allSource)
}