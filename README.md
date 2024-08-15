# 祥子 Sakiko - 次世代的Java Hook解决方案

> **注意**
>
> 本项目**Fork**自**Karlatemp**的**JvmHookFramework**
>
> **原开发者**表示**不适合**在**正式环境**使用，速度**较慢**
>
> 由于**开发不便**，暂不提供**macOS平台**的**本机库CI构建**
>
> 项目名称源自于 **BanG Dream! It's MyGO!!!!!** 中的角色**丰川祥子**

## 概述

Sakiko是一款跨平台的JavaHook方案，旨在提供更灵活的JVM内部Hook机制。目前已支持在桌面端JVM中通过JvmTI进行Hook。

## 特性

- **跨平台支持**：适用于多种操作系统，方便不同平台的开发者使用。
- **提供API与本机库**：开发者可以通过提供的API和本机库进行Hook操作。

## 下载

1. 前往 [Actions](https://github.com/EarzuChan/Sakiko/actions)
2. 找到最后一个成功运行的Action
3. 向下滚动，在Artifacts部分下载Jars和相应平台的本机库（例如windows-shared-x64）。

## 运行

下载后，您应该看到以下文件：

- **Jars.zip**：
    - `api-old-0.11.4.514.jar` - **旧版**开发者API包
    - `sakiko-old-0.11.4.514-core.jar` - **旧版**运行时Java库
        - `sakiko-old-0.11.4.514-launcher.jar` - **旧版**启动器包（Java Agent）
- **平台-架构.zip**：
    - `libnative.dll` / `libnative.so` - 本机库

使用Sakiko，您需要修改Java命令行，格式如下：

```shell
java -agentpath:/path/to/libnative.so -javaagent:/path/to/sakiko-old-0.11.4.514-launcher.jar ...
```

## 克隆与构建

请注意，在克隆并推送到您自己的远程仓库时，务必确保提交中包含`CMakeLists.txt`，否则CI构建将失败。

## 旧模块范式【已过时】

### 开发模块

新建一个**生成Jar**的**Java Library**项目，并将`api-old`包添加为**项目依赖**。

将一个名为`module_entries`的文件放在**Jar的根目录**下，文件的**每一行**代表**一个入口点类名**。

在您的**入口类**中创建一个**静态方法**`load`，通过`io.github.karlatemp.jvmhook.JvmHookFramework`类的**静态字段**`INSTANCE`获取**预先提供的**`JvmHookFramework`实例，进行**Hook操作**。

示例：

```java
public class MyModule
{
    public static void load()
    {
        // 度尽劫波兄弟在，相逢一笑泯恩仇
    }
}
```

### 安装模块

首次正确运行Java命令后，将在运行目录创建一个名为`sakiko-modules`的文件夹。将模块放入该文件夹。

如果希望将模块放在其他目录下，只需设置环境变量`SAKIKO_MODULES`为您放置模块的目录即可。

Shell脚本示例：

```shell
#!/usr/bin/env bash

SAKIKO_MODULES=/path/to/other/dir
java ...
```

## 新模块范式【待开发】

### 开发模块

您需要新建一个**打包成Jar**的**JavaLibrary**项目，并根据以下格式填写配置项文件。

```yaml
generator:
  target-type: COMPAT
  use-xxx: true # 指定使用一些特定的功能（参见文档）

hook-module:
  package-name: com.mamba # 您的模块包名
  java-entry: com.mamba.MambaModule # 您的Java库入口点
  native-entry: mamba # 您的本机库入口点
  description: My new Hook Module. # 模块描述
  api-level: 1919810 # API等级
  host-scopes: # 主机作用域
    - com.xxx.universe.system # 一条主机作用域
```

将配置项文件命名为**module_profile**，并放在**Jar的根目录**下。

开发新版本模块需要使用Kotlin语言，以下是一个示例代码片段：

```kotlin
class MambaModule(hookContext: HookContext) : SakikoBaseModule(hookContext) {
    init { // 初始化
        SakikoHookAPI.configs { // 配置
            isDebug = false // 是否开启调试
        }
    }

    override fun onHook() {
        // Hook指定的单个方法
        "com.demo.Test".toClass().method {
            name = "test"
            param(StringClass)
        }.hook {
            before {
                // Do something...
            }
            after {
                // Do something...
            }
        }

        // Hook匹配的所有方法
        "com.demo.Test".toClass().method {
            name = "test"
        }.all().hook {
            before {
                // Do something...
            }
            after {
                // Do something...
            }
        }

        // Hook多个目标
        "com.demo.Test".toClass().apply {
            method {
                name = "test"
                param(StringClass)
            }.hook {
                before {
                    // Do something...
                }
                after {
                    // Do something...
                }
            }
            method {
                name = "another"
                param(IntType)
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

### 安装模块

【待计划】

### 说明

这种范式受到未发布的新版YukiHook的启发，而且还没开始实现。

该新Hook模块范式旨在提供一个灵活且易于配置的方式来创建和使用Hook模块。通过设置jar元数据和实现主类中的Hook逻辑，用户可以轻松地对指定的类和方法进行Hook。

## 展望

- **KotlinHookAPI**：未来计划开发一套新的KotlinHookAPI，提供更便捷的Hook方式。
- **安卓端Hook支持**：通过LSPosed的LSPlant，实现对安卓平台的Hook。
- **桌面端内存修改Hook**：计划为桌面端JVM平台开发类似LSPlant的内存修改Hook方案，尽管可能对特定的JVM版本和型号有要求，适用性可能较差。