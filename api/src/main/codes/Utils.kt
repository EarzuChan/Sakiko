package me.earzuchan.sakiko.api.utils

object Log {
    enum class Level {
        DEBUG, INFO, WARN, ERROR
    }

    private var currentLevel: Level = Level.INFO

    fun setLevel(level: Level) {
        currentLevel = level
    }

    fun debug(tag: String, message: String) {
        if (currentLevel <= Level.DEBUG) {
            println("[DEBUG] $tag > $message")
        }
    }

    fun info(tag: String, message: String) {
        if (currentLevel <= Level.INFO) {
            println("[INFO] $tag > $message")
        }
    }

    fun warn(tag: String, message: String) {
        if (currentLevel <= Level.WARN) {
            println("[WARN] $tag > $message")
        }
    }

    fun error(tag: String, message: String) {
        if (currentLevel <= Level.ERROR) {
            println("[ERROR] $tag > $message")
        }
    }
}