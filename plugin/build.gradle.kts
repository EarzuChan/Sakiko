plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        create("sakikoPlugin") {
            id = "me.earzuchan.sakiko.plugin"
            implementationClass = "me.earzuchan.sakiko.plugin.SakikoPlugin"
            displayName = "Sakiko Plugin"
            description = "A tiny gradle plugin that helps build Sakiko Modules."
        }
    }
}