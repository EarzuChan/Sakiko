# 祥子 Sakiko - 一款适用于JVM的Hook框架

> 注意
>
> 本项目Fork自`Karlatemp`的`JvmHookFramework`
>
> 原开发者说不适合在正式环境使用, 很慢（悲
> 
> 另外由于麻烦，暂时不提供macOS平台的本机库CI构建
>
> 本库的名字来自于`BanG Dream! It's MyGO!!!!!`中的丰川祥子

## 下载

点击 [Actions](https://github.com/EarzuChan/Sakiko/actions)
点击最后一个成功运行的`Action`

拖到最下面, 在`Artifacts`中点击`Jars`和为相应平台构建的本机库（如 `windows-shared-x64`）

## 运行

下载之后, 应该有如下文件

```text
Jars.zip
    `- api-1.0.0.jar                        -  The api for developers
    `- jvm-hook-framework-core.jar          -  Runtime java library (shadowed)
    `- jvm-hook-framework-launcher.jar      -  JHF Launcher (Javaagent)
    `- jvm-hook-framework-launcher-obf.jar  -  JHF Launcher (Javaagent) (Relocated)
    `- jvm-hook-framework-obf.jar           -  JHF Launcher (Javaagent) (Relocated) (Not runnable)
NativeLib.zip
    `- libnative.dll / libnative.so         - native lib (native agent)
```

你需要按照如下的格式修改你的Java命令行来使用Sakiko

```shell
java -agentpath:/path/to/libnative.so -javaagent:/path/to/jvm-hook-framework-launcher.jar ...
# Or
java -agentpath:/path/to/libnative.so -javaagent:/path/to/jvm-hook-framework-launcher-obf.jar ...
```

## 克隆与构建

注意克隆后如果再推送到你自己的新远程仓库，注意提交里要包含CMakelists.txt，不然CI构建会失败（悲

## 安装扩展【旧扩展范式】

第一次正确运行Java命令后, 会在运行目录创建一个名为`jvm-hook-framework-extensions`的文件夹

把扩展放进这个文件夹里

想要把扩展放到其它的目录下？只需要把自定义文件夹路径设置为系统环境变量`JVM_HOOK_FRAMEWORK_EXTENSIONS`

```shell
#!/usr/bin/env bash

JVM_HOOK_FRAMEWORK_EXTENSIONS=/path/to/other/dir
java ....
```

## 开发扩展【旧扩展范式】

新建一个生成Jar的Java库项目，并将Api包添加为项目依赖

将一个名为`jvm-hook-ext.txt`的文件放在Jar的根目录下，文件内容是您的扩展类的完整类名

在您的类中创建一个静态方法`load`，通过`io.github.karlatemp.jvmhook.JvmHookFramework`类的静态字段`INSTANCE`获取事先提供的的`JvmHookFramework实例`，这样就可以爽Hook了（喜

示例：[TestExtension](testunit/src/main/java/teunit/ext/Ext.java)