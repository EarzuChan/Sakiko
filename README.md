# Sakiko - A Next-Generation JVM Hooking Framework

[中文](README_CN.md)

> **Advisory**
>
> This framework is currently **experimental** and may exhibit **performance constraints** in production environments.
>
> Named after **Togawa Sakiko** from **BanG Dream! It's MyGO!!!!!**

---

## Overview

Sakiko is an ambitious multiplatform hooking framework designed to provide Kotlin developers with unified hooking capabilities across multiple platforms.

## Key Features

- **Multiplatform Architecture**: Initial support for standard JVM and Android platforms.
- **Developer-Centric API**: Intuitive API design complemented by comprehensive tooling, including dedicated loaders.

## Development

Clone the repository and build using your preferred IDE or build tools.

### Project Structure
- `/api` - Core hooking API
- `/launcher-jvm` - JVM platform loader
- `/core-<platform>` - Platform-specific implementations
- `/test` - Some unit tests
- `/test-module` - Test modules
- `/test-<platform>` - Platform-specific test runners

## Usage Guide

### Creating Modules

1. **Build the API**
   ```bash
   gradle api jar
   ```
   This generates `sakiko-<version>-api.jar` in `/api/build/libs/`.

2. **Set Up Your Module Project**

   Create a new Kotlin Library project.

3. **Configure Dependencies**

   In your `build.gradle.kts`:
   ```kotlin
   dependencies {
       compileOnly(project(":api"))
   }
   ```

4. **Begin Development**

### Example Module

```kotlin
@ExposedSakikoModule
class ExampleModule : SakikoBaseModule {
    override fun onHook() = encase {
        // Hook a specific method
        "com.demo.Test".toClass().resolve().firstMethod {
            name = "test"
            parameters(StringClass)
        }.hook {
            before {
                // Pre-execution logic
            }
            after {
                // Post-execution logic
            }
        }

        // Hook multiple methods elegantly
        "com.demo.Test".toClass().resolve().apply {
            firstMethod {
                name = "test"
                parameters(StringClass)
            }.hook {
                before { /* ... */ }
                after { /* ... */ }
            }

            firstMethod {
                name = "another"
                parameters(IntType)
            }.hook {
                before { /* ... */ }
                after { /* ... */ }
            }
        }
    }
}
```

**Note**: The API is subject to refinement. Please monitor updates regularly.

### JVM Implementation

1. **Build the Launcher**
   ```bash
   gradle launcher-jvm:pack
   ```
   This generates `sakiko-<version>-launcher-jvm.jar` in `/launcher-jvm/build/libs/`

2. **Prepare Your Module**

   Package your module as a JAR file.

3. **Configure JVM Arguments**
   ```bash
   -javaagent:/path/to/launcher.jar=/path/to/your/module.jar
   ```

   **Multiple modules**:
   ```bash
   -javaagent:/path/to/launcher.jar=/path/module1.jar,/path/module2.jar
   ```

4. **Launch Your Application**

   The hooks will be activated automatically upon JVM startup.

   **Direct hooking**: Add the API as a `compileOnly` dependency and use:
   ```bash
   -javaagent:/path/to/launcher.jar
   ```

### Android Implementation

Add the Android core and your module as `implementation` dependencies, then load modules programmatically:

```kotlin
Runner.loadModule(YourModule::class)
```

**Coming Soon**: Dedicated Android launcher for streamlined integration.

### Design Philosophy

While inspired by YukiHookAPI's paradigm, Sakiko implements fundamentally different architectural approaches.

## Roadmap

- **API Refinement**: Continuous optimization of both API design and hooking performance.
- **Advanced Hooking Framework**: Development of memory-modification-based hooking (similar to LSPlant/Frida) for JVM platforms, offering enhanced capabilities despite potential version-specific requirements.

## Community

We welcome contributions from the community:

1. **Star the Repository**: Show your support
2. **Issues & Discussions**: Share feedback, questions, or suggestions
3. **Join Development**: We're actively seeking contributors—reach out via Issues
4. **Pull Requests**: Submit improvements (direct collaboration is preferred)
5. **Sponsorship**: Support ongoing development

## Acknowledgments

- [YukiHookAPI](https://github.com/highcapable/yukihookapi): API design patterns and implementation insights.
- [JvmPosed](https://github.com/cinit/jvmxposed): JVM bytecode verification bypass techniques and elegant bytecode weaving approaches.