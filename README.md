# Sakiko - 次世代的JVM Hook方案

[![Maven](https://img.shields.io/badge/Maven-EarzuChan-blue?style=flat-square)](https://earzuchan.github.io/maven/)
[![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)](https://opensource.org/license/MIT)
[![Release](https://img.shields.io/github/v/release/earzuchan/sakiko?style=flat-square)](https://github.com/earzuchan/sakiko/releases)

[English](README_EN.md) | [Deutsch](README_DE.md)

> **注意**
>
> **不太适合**在**生产环境**使用，可能**速度较慢**或产生**未定义行为**
>
> 项目名称源自于 **BanG Dream! It's MyGO!!!!!** 中的角色**丰川祥子**
>
> **还在钩，还在钩**（指**Hooking**）

![Sakiko](art/banner.png)

**Sakiko**是一个为**Kotlin开发者**而生的**多平台Hook框架**。它旨在通过**一套统一的API**（`HookAPI` + `ModuleAPI`），为您提供**多平台一致的Hook能力**

## ✨ 特性

*   **面向Kotlin设计**：专为Kotlin设计，充分利用其语言特性
*   **多平台**：一套代码，同时支持Android与桌面JVM平台
*   **统一API**：简洁、易用的`HookAPI`和`ModuleAPI`
*   **自动发现**：通过注解自动发现并加载模块入口，简化配置
*   **类隔离**：为模块提供独立的ClassLoader，避免冲突

## 🔩 项目结构

*   `api`：核心API，包含`HookAPI`与`ModuleAPI`
*   `core-*`：各平台的核心实现
    *   `core-android`：暂时采用AliuHook（LSPlant的包装），实现Android Hook
    *   `core-jvm`：参考了JvmXposed，实现桌面JVM Hook
*   `launcher-*`：各平台的启动器，负责加载模块
    *   `launcher-android`：安卓库
    *   `launcher-jvm`：Java Agent
*   `native-*`：各平台底层Native实现
    *   `native-android`：暂未提供
    *   `native-jvm`：使用`Kotlin/Native`实现
*   `plugin`：Gradle插件，暂未提供
*   `test*`：测试与示例项目
    *  `test`：用来测试被Hook（即等同于`目标应用程序`）
    *  `test-*`：既用作测试，也是示例，展示了在对应平台上如何使用Launcher加载Module们
    *  `test-module`：示例模块（含三个Entry，两个对`test`实行Hook，以及一个单元测试）

## 🚀 快速入门

### 1. 添加Maven仓库

请将以下Maven仓库地址添加到您的项目中：

```kotlin
maven("https://earzuchan.github.io/Maven/") // 注意：Maven的M要大写
maven("https://maven.aliucord.com/releases/") // 这是为了能找到`core-api`所依赖的AliuHook
```

库的版本请查看[Release页面](https://github.com/Earzuchan/Sakiko/releases)

### 2. 创建Hook模块

1.  新建一个Kotlin/JVM项目
2.  以`compileOnly`的方式依赖`api`模块：`compileOnly("me.earzuchan.sakiko:api:<version>")`
3.  实现`SakikoModuleEntry`接口，并使用`@ExposedSakikoModuleEntry`注解标记入口类

您可能需要阅读[本文档](https://highcapable.github.io/KavaRef/)以学习KavaRef API的使用

示例代码如下：

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
        // `encase`注入了HookContext
        // 通过它，可访问appClassLoader、String.toClass(cl=appClassLoader)等
        SLog.info("示例模块入口：开始 Hook StaticMethods", TAG)

        val targetClass = "your.target.app.StaticMethods".toClass().resolve()

        // 查找方法并Hook，替换返回值
        targetClass.firstMethod { name = "method1" }.hook {
            replaceTo(1919810) // 注意：你不可同时使用 replaceTo 与 before/after
        }

        // 查找方法（提供参数类型以进行更精确的匹配，不提供也行，具体参见KavaRef文档）并Hook，在执行前修改参数或结果
        targetClass.firstMethod {
            name = "method2"
            parameters(String::class)
        }.hook {
            before {
                SLog.info("Before method2：original arg[0] = ${args[0]}", TAG)
                val thizObj = instance // 获取this对象
                val a = args[0] as String // 获取参数，以后会提供更方便的API
                
                args[0] = "Modified by Sakiko" // 修改参数，会在原方法执行时生效
                
                // 注意：以下这俩会相互覆盖
                result = "修改返回值" // 手动设置result会提前返回，跳过原方法执行
                throwable = Exception() // 设置异常，以后这个API会改
                
            }
            
            after {
                SLog.info("After method2：result = $result", TAG)
            }
        }
    }
}
```

### 3. 加载模块

将您的模块打包成Jar（for JVM）或Dex（for Android）

#### 在Android平台

1.  在您的安卓项目中以`implementation`形式依赖`launcher-android`：`implementation("me.earzuchan.sakiko:launcher-android:<version>")`
2.  您可以将模块的代码编译成Dex文件（通过安卓SDK附带的`D8`工具，具体如何使用可以参考[这里的任务配置](test-module/build.gradle.kts)）。当然您也可以将模块一同编译进您的安卓项目中，我们提供了多种加载方式
3.  在合适的时机（如`Application.onCreate`）调用`Launcher`加载模块即可

```kotlin
import me.earzuchan.sakiko.launcher.Launcher

// 在Application或Activity的onCreate中（也可以是其它任意时机；最好在被Hook的代码执行前，以使Hook效果最大化）
// 假设你的`dex`文件路径为/data/data/your.app/files/module.dex
Launcher.findAndLoadModuleFromDexByPath("/path/to/your/module.dex") // 注意，在太新的系统上，访问可写的Dex可能会被系统限制
// 也支持从ClassLoader或Class对象加载
// Launcher.findAndLoadModuleFromClassLoader(classLoader)
// Launcher.loadModuleFromClass(YourModuleEntry::class.java)
```

#### 在JVM平台

1.  下载`core-jvm.jar`和`launcher-jvm.jar`（通过我们的Maven仓库或Release页面）
2.  设置环境变量`SAKICORE`指向`core-jvm.jar`的绝对路径
3.  在启动目标应用程序时，添加以下JVM参数：

```bash
# SAKICORE=/path/to/core-jvm.jar
java -noverify -javaagent:"/path/to/launcher-jvm.jar=/path/to/module1.jar;/path/to/module2.jar" -jar /path/to/target-app.jar
```
*   `-noverify`：目前还需要此参数，未来版本将内置验证绕过能力
*   `-javaagent`：多个模块Jar路径之间请使用`;`分隔

## 🛠️ 开发本项目

1.  克隆本仓库
2.  使用Gradle构建项目

常用Gradle任务：
*   打包`api`：`./gradlew :api:jar`（可能需先运行`:api:generateBuildConstants`以生成构建常量）
*   打包`core-jvm`：`./gradlew :core-jvm:packageCore`
*   打包`launcher-jvm`：`./gradlew :launcher-jvm:packageLauncher`
*   构建并集成`native-jvm`：`./gradlew :core-jvm:buildAndCopyNativeLibs`

## 💬 社区与支持

联系邮箱：huascq@gmail.com
我们欢迎任何形式的贡献与交流：

*   **Star**本项目
*   提交**Issue**报告Bug或提出建议
*   发起**Pull Request**贡献代码
*   **加入开发团队**（请通过Issue告知我们，或者是发邮件联系我们）
*   提供**赞助**

## 🙏 致谢

*   [LSPosed](https://github.com/LSPosed/LSPosed)：学习其Hook执行控制流与能力范围
*   [LSPlant](https://github.com/LSPosed/LSPlant) & [AliuHook](https://github.com/AliuCord/Hook)：作为当前Android侧的底层Hook实现
*   [JvmXposed](https://github.com/cinit/JvmXposed)：学习其JVM Hook的实现方式
*   [YukiHookAPI](https://github.com/HighCapable/YukiHookAPI)：本项目的API设计受到了它的启发
*   [KavaRef](https://github.com/HighCapable/KavaRef)：优秀的Kotlin反射库，已集成在`api`模块中，无需额外引入
*   [ClassGraph](https://github.com/ClassGraph/ClassGraph) & [DexKit](https://github.com/LuckyPray/DexKit)：用于在各平台发现模块入口
*   [ASM](https://gitlab.ow2.org/Asm/Asm)：强大的JVM字节码操作库，用于在JVM向目标类织入Hook序言

## 📄 许可证

本项目采用**MIT**许可证