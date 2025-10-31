# Sakiko - 次世代的JVM Hook方案

> **注意**
>
> **不太适合**在**正式环境**使用，可能**速度较慢**
>
> 项目名称源自于 **BanG Dream! It's MyGO!!!!!** 中的角色**丰川祥子**
>
> **还在钩，还在钩**（指**Hooking**）

---

## 概述

Sakiko是一款计划能够跨平台的JVM Hook方案，旨在为Kotlin开发者提供多平台统一的Hook能力。

## 特性

- **多平台支持**：目前初步支持JVM与安卓平台。
- **提供API及配套工具**：我们的API十分易用，且为您提供了加载器等方便您使用的配套工具。

## 开发

克隆项目，然后手动或者使用您最钟意的IDE进行构建。

### Project Structure
- `/api` - 核心Hook API
- `/launcher-jvm` - JVM平台加载器
- `/core-<platform>` - 平台特定的实现
- `/test` - 一些单元测试
- `/test-module` - 测试模块
- `/test-<platform>` - 平台特定的测试运行器

## 使用

### 开发模块：

1. 通过命令`gradle api jar`构建我们API的Jar包。
    - 该命令会在`/api/build/libs/`目录下生成一个名为`sakiko-版本号-api.jar`的文件
2. 新建您的模块项目：一个Kotlin Library项目。
    - 开发模块需要使用Kotlin语言
3. 为您的模块项目添加对我们API的依赖。
    - 以使用Gradle管理项目为例：在您的`build.gradle.kts`文件中添加以下内容：

    ```kotlin
    dependencies {
        compileOnly(project(":api"))
    }
    ```

4. 愉快地开发。

以下是一段使用我们的API开发模块的示例代码：

```kotlin
@ExposedSakikoModule
class ExampleModule : SakikoBaseModule {
    // 配置
    // TODO

    override fun onHook() = encase {
        // Hook指定的单个方法
        "com.demo.Test".toClass().resolve().firstMethod {
            name = "test"
            parameters(String::class)
        }.hook {
            before {
                // Do something...
            }
            after {
                // Do something...
            }
        }

        // Hook匹配的所有方法
        // TODO

        // Hook多个目标
        "com.demo.Test".toClass().resolve().apply {
            firstMethod {
                name = "test"
            }.hook {
                before {
                    // Do something...
                }
                after {
                    // Do something...
                }
            }

            firstMethod {
                name = "another"
            }.hook {
                before {
                    // Do something...
                }
                after {
                    // Do something...
                }
            }
        }
    }
}
```

注意：API或将会修订，请您时时留意。

### 在JVM上进行Hook

1. 通过命令`gradle launcher-jvm:pack`构建启动器的Jar包。
    - 该命令会在`/launcher-jvm/build/libs/`目录下生成一个名为`sakiko-版本号-launcher-jvm`的文件
2. 生成您模块的Jar包。
3. 在您启动JVM的命令行中加入`-javaagent:[启动器Jar包的路径]=[您模块Jar包（们）的路径]`参数。
    - 例如：`-javaagent:/path/to/launcher.jar=/path/to/your/module.jar`
    - 我们支持同时加载多个模块，您可以使用逗号`,`分隔多个模块的Jar包路径，如
      `-javaagent:/path/to/launcher.jar=/path/module1.jar,/path/module2.jar`
4. 启动您的JVM应用程序，如无异常，Hook将生效。
    - 如若您想在应用程序中直接进行Hook，为应用程序添加对API的`compileOnly`式依赖和添加JVM启动参数（无需加载模块的话是：
      `-javaagent:[启动器Jar包的路径]`）即可。

### 在安卓上进行Hook

为您的应用程序添加对安卓核心和您模块的`implementation`式依赖，然后通过调用安卓核心的API进行模块加载：

```kotlin
Runner.loadModule(YourModule::class) // YourModule是您模块的类名
```

注意：未来或将提供适用于安卓的启动器，敬请期待。

### 说明

这种范式受到YukiHookAPI的启发，但具体实现方式有所不同。

## 展望

- **持续打磨API和Hooking性能优化**：我们会长期以往地优化本项目，以期为开发者们提供更好的体验。请您时时留意API变更。
- **新的Hook框架**：计划为JVM平台开发类似LSPlant与Frida的内存修改Hook方案，尽管可能对特定的JVM版本和型号有要求，但可以打造更强大更
  ~~搞笑~~高效的Hook方案。

## 社区支持

我们希望各位大手子能够为我们提供一些支持，这真的对我们很重要：

1. **Star**：给我们一个Star，让我们知道您热爱我们的项目。
2. **Issue与讨论**：如果您有任何问题或者建议，请在Issue和Discussion处留言。
3. **参与开发**：我们真的很缺有能人士，目前您若想直接参与开发组工作，请在Issue处留言
4. **Pull Request**：如果您有更好的想法或者代码，也可以选择提交PR。当然最好您能够直接参与开发。
5. **赞助**：如果您愿意，您可以~~通过给我们打钱~~赞助我们来支持我们的开发。

## 致谢

- [YukiHookAPI](https://github.com/highcapable/yukihookapi)：对其的API设计和实现原理进行了学习借鉴。
- [JvmPosed](https://github.com/cinit/jvmxposed)：从其中学到了绕过JVM字节码校验的方法，以及一种优雅的字节码织入形式。