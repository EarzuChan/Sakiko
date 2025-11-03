package me.earzuchan.sakiko.test

fun main() {
    println("开始测试")

    require(StaticMethods.method1() == 1919810) { "Hook Method1 失败" }
    require(StaticMethods.method2() == "恩情") { "Hook Method2 失败" }
    require(StaticMethods.method3() == 6L) { "明明没有Hook Method3" }

    val instance = InstanceMethods()
    require(instance.method1() == 142857) { "Hook Method1 失败" }
    require(instance.method2() == "南下") { "Hook Method2 失败" }
    require(instance.method3() == 6L) { "明明没有Hook Method3" }

    require(instance.method1() == 142857) { "Hook Method1 失败" }
    require(instance.method2() == "南下") { "Hook Method2 失败" }
    require(instance.method3() == 6L) { "明明没有Hook Method3" }

    require(Ctor("你的盐").value == "我的醋") { "Hook Ctor 失败" }

    println("完成测试")
}

object StaticMethods {
    @JvmStatic
    fun method1() = 114514

    @JvmStatic
    fun method2() = "主体"

    @JvmStatic
    fun method3() = 6L
}

class InstanceMethods {
    fun method1() = 114514

    fun method2() = "主体"

    fun method3() = 6L
}

class Ctor(val value: String)