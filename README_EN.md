# Sakiko - Next-Generation JVM Hook Solution

[![Maven](https://img.shields.io/badge/Maven-EarzuChan-blue?style=flat-square)](https://earzuchan.github.io/maven/)
[![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)](https://opensource.org/license/MIT)
[![Release](https://img.shields.io/github/v/release/earzuchan/sakiko?style=flat-square)](https://github.com/earzuchan/sakiko/releases)

[中文](README.md) | [Deutsch](README_DE.md)

> **Note**
>
> **Maybe not suitable** for use in a **production environment**, potentially **slow** or causing **undefined behavior**
>
> Project name is inspired by the character **Sakiko Togawa** from **BanG Dream! It's MyGO!!!!!**
>
> **Still GOing, still GOing** (A **meme** about MyGO. In **Chinese context**, **go** sounds similar to **hook** (钩), hence **a pun** can be made)

![Sakiko](art/banner.png)

**Sakiko** is **a multi-platform Hook framework** built for **Kotlin developers**. It aims to provide **consistent cross-platform Hook capabilities** through **a unified API** (`HookAPI` + `ModuleAPI`)

## ✨ Features

*   **Designed for Kotlin**: Specifically designed for Kotlin, fully leveraging its language features
*   **Multi-platform**: One codebase supporting both Android and desktop JVM platforms
*   **Unified API**: Simple and easy-to-use `HookAPI` and `ModuleAPI`
*   **Auto-discovery**: Automatically discovers and loads module entries through annotations, simplifying configuration
*   **Class Isolation**: Provides independent ClassLoaders for modules to avoid conflicts

## 🔩 Project Structure

*   `api`: Core API, containing `HookAPI` and `ModuleAPI`
*   `core-*`: Platform-specific core implementations
    *   `core-android`: Currently uses AliuHook (LSPlant wrapper) for Android Hook implementation
    *   `core-jvm`: Implements desktop JVM Hook, inspired by JvmXposed
*   `launcher-*`: Platform-specific launchers responsible for loading modules
    *   `launcher-android`: Android library
    *   `launcher-jvm`: Java Agent
*   `native-*`: Platform-specific native implementations
    *   `native-android`: Not yet provided
    *   `native-jvm`: Implemented using `Kotlin/Native`
*   `plugin`: Gradle plugin, not yet provided
*   `test*`: Test and example projects
    *  `test`: Acts as the Hook target (equivalent to the `target application`)
    *  `test-*`: Serves as both tests and examples, demonstrating how to use the Launcher to load Modules on corresponding platforms
    *  `test-module`: Example module (contains three Entries: two for hooking `test`, and one unit test)

## 🚀 Quick Start

### 1. Add Maven Repository

Add the following Maven repository URLs to your project:

```kotlin
maven("https://earzuchan.github.io/Maven/") // Note: Maven's M must be capitalized
maven("https://maven.aliucord.com/releases/") // This is to find AliuHook, which core-api depends on
```

For library versions, please check the [Release page](https://github.com/Earzuchan/Sakiko/releases)

### 2. Create a Hook Module

1.  Create a new Kotlin/JVM project
2.  Add the `api` module as a `compileOnly` dependency: `compileOnly("me.earzuchan.sakiko:api:<version>")`
3.  Implement the `SakikoModuleEntry` interface and mark the entry class with the `@ExposedSakikoModuleEntry` annotation

You may need to read [this documentation](https://highcapable.github.io/KavaRef/) to learn how to use the KavaRef API

Example code:

```kotlin
package your.module

import me.earzuchan.sakiko.api.annotations.ExposedSakikoModuleEntry
import me.earzuchan.sakiko.api.hook.hook
import me.earzuchan.sakiko.api.module.SakikoModuleEntry
import me.earzuchan.sakiko.api.utils.SLog
import com.highcapable.kavaref.KavaRef.Companion.resolve

@ExposedSakikoModuleEntry
class TestModuleEntry : SakikoModuleEntry {
    private val TAG = "TestModuleEntry"

    override fun onHook() = encase {
        // `encase` injects HookContext
        // Through it, you can access appClassLoader, String.toClass(cl=appClassLoader), etc.
        SLog.info("Example module entry: Starting to Hook StaticMethods", TAG)

        val targetClass = "your.target.app.StaticMethods".toClass().resolve()

        // Find and hook method, replace return value
        targetClass.firstMethod { name = "method1" }.hook {
            replaceTo(1919810) // Notice: you cannot use replaceTo together with before/after
        }

        // Find method (provide parameter types for more precise matching; optional, see KavaRef documentation) and hook, modify parameters or results before execution
        targetClass.firstMethod {
            name = "method2"
            parameters(String::class)
        }.hook {
            before {
                SLog.info("Before method2: original arg[0] = ${args[0]}", TAG)
                val thizObj = instance // Get the this object
                val a = args[0] as String // Get parameter, more convenient APIs will be provided in the future
                
                args[0] = "Modified by Sakiko" // Modify parameter, will take effect when the original method executes
                
                // Notice: the following two will override each other
                result = "Modified return value" // Manually setting result will return early, skipping original method execution
                throwable = Exception() // Set exception, this API will be changed in the future
                
            }
            
            after {
                SLog.info("After method2: result = $result", TAG)
            }
        }
    }
}
```

### 3. Load the Module

Package your module as a Jar (for JVM) or Dex (for Android)

#### On Android Platform

1.  In your Android project, add `launcher-android` as an `implementation` dependency: `implementation("me.earzuchan.sakiko:launcher-android:<version>")`
2.  You can compile the module's code into a Dex file (using the `D8` tool included with the Android SDK; for details on how to use it, refer to [the task configuration here](test-module/build.gradle.kts)). Alternatively, you can compile the module directly into your Android project; we offer various loading methods
3.  Call `Launcher` to load the module at an appropriate time (e.g., in `Application.onCreate`)

```kotlin
import me.earzuchan.sakiko.launcher.Launcher

// In Application or Activity's onCreate (or any other timing; preferably before hooked code executes to maximize Hook effectiveness)
// Assuming your `dex` file path is /data/data/your.app/files/module.dex
Launcher.findAndLoadModuleFromDexByPath("/path/to/your/module.dex") // Note: on newer systems, accessing writable Dex may be restricted by the system
// Also supports loading from ClassLoader or Class objects
// Launcher.findAndLoadModuleFromClassLoader(classLoader)
// Launcher.loadModuleFromClass(YourModuleEntry::class.java)
```

#### On JVM Platform

1.  Download `core-jvm.jar` and `launcher-jvm.jar` (via our Maven repository or Release page)
2.  Set the environment variable `SAKICORE` to point to the absolute path of `core-jvm.jar`
3.  When starting the target application, add the following JVM parameters:

```bash
# SAKICORE=/path/to/core-jvm.jar
java -noverify -javaagent:"/path/to/launcher-jvm.jar=/path/to/module1.jar;/path/to/module2.jar" -jar /path/to/target-app.jar
```
*   `-noverify`: Currently still required; future versions will include built-in verification bypass capability
*   `-javaagent`: Separate multiple module Jar paths with `;`

## 🛠️ Developing This Project

1.  Clone this repository
2.  Build the project using Gradle

Common Gradle tasks:
*   Package `api`: `./gradlew :api:jar` (may need to run `:api:generateBuildConstants` first to generate build constants)
*   Package `core-jvm`: `./gradlew :core-jvm:packageCore`
*   Package `launcher-jvm`: `./gradlew :launcher-jvm:packageLauncher`
*   Build and integrate `native-jvm`: `./gradlew :core-jvm:buildAndCopyNativeLibs`

## 💬 Community & Support

Contact email: huascq@gmail.com
We welcome any form of contribution and communication:

*   **Star** this project
*   Submit **Issues** to report bugs or make suggestions
*   Create **Pull Requests** to contribute code
*   **Join the development team** (please let us know via Issue or contact us by email)
*   Provide **sponsorship**

## 🙏 Acknowledgments

*   [LSPosed](https://github.com/LSPosed/LSPosed): Learning from its Hook execution flow and capability scope
*   [LSPlant](https://github.com/LSPosed/LSPlant) & [AliuHook](https://github.com/AliuCord/Hook): Current underlying Hook implementation for Android
*   [JvmXposed](https://github.com/cinit/JvmXposed): Learning from its JVM Hook implementation approach
*   [YukiHookAPI](https://github.com/HighCapable/YukiHookAPI): The API design of this project was inspired by it
*   [KavaRef](https://github.com/HighCapable/KavaRef): Excellent Kotlin reflection library, integrated in the `api` module, no additional import needed
*   [ClassGraph](https://github.com/ClassGraph/ClassGraph) & [DexKit](https://github.com/LuckyPray/DexKit): Used for discovering module entries on each platform
*   [ASM](https://gitlab.ow2.org/Asm/Asm): A powerful JVM bytecode manipulation library used to weave Hook prologues into target classes on JVM

## 📄 License

This project is licensed under the **MIT** License