package me.earzuchan.sakiko.core

import com.highcapable.kavaref.KavaRef.Companion.resolve
import me.earzuchan.sakiko.api.hook.HookConfig
import me.earzuchan.sakiko.api.hook.SakikoHookPriority
import me.earzuchan.sakiko.api.hook.hook
import me.earzuchan.sakiko.api.hook.hookMember
import me.earzuchan.sakiko.api.utils.SLog
import me.earzuchan.sakiko.core.utils.MambaUtils
import java.lang.reflect.Modifier
import kotlin.math.abs

/*
open class Vater {
    open fun a() = "Fuck you"
    open fun b() = "Suck your Dick"
}

class Sohn : Vater() {
    override fun a() = "Fuck me"
    // NOT OVERRIDIN B
}

class Man {
    fun whatHeCanSay() = "Mamba out"
    fun kobe() = 111
}

fun main() {
    SakiBridgeImpl.init()

    // testVaterSohn()
    // testMultiAndPriority()
    testHookUnhook()
}

fun testVaterSohn() {
    Vater::class.resolve().apply {
        firstMethod { name = "a" }.hook {
            before {
                SLog.debug("bef vater a")
                SLog.debug("result: ${callOriginal()}")
                SLog.debug("aft callin org vater a")
            }
        }

        firstMethod { name = "b" }.hook {
            before {
                SLog.debug("bef vater b")
                SLog.debug("result: ${callOriginal()}")
                SLog.debug("aft callin org vater b")
            }
        }
    }

    Sohn::class.resolve().apply {
        // RESULT：仅仅SOHN
        firstMethod { name = "a" }.hook {
            before {
                SLog.debug("bef sohn a")
                SLog.debug("result: ${callOriginal()}")
                SLog.debug("aft callin org sohn a")
            }
        }

        // RESULT：完全等于Vater的bHook
        firstMethod {
            name = "b"
            superclass() // u kno
        }.hook {
            before {
                SLog.debug("bef sohn b")
                SLog.debug("result: ${callOriginal()}")
                SLog.debug("aft callin org sohn b")
            }
        }
    }

    SLog.info("\n\nvater")
    val vater = Vater()
    vater.a()
    vater.b()

    SLog.info("\n\nsohn")
    val sohn = Sohn()
    sohn.a()
    sohn.b()

    // RESULT：跟直接Sohn的触发结果一样，就是实例本身的类型管用
    SLog.info("\n\njunge")
    val junge = Sohn() as Vater
    junge.a()
    junge.b()
}

fun testMultiAndPriority() {
    val manClass = Man::class.resolve()

    val whatHeCanSayMethod = manClass.firstMethod { name = "whatHeCanSay" }
    val kobeMethod = manClass.firstMethod { name = "kobe" }

    // WCS RESULT：123-321

    whatHeCanSayMethod.hook {
        before {
            SLog.debug("bef wc 1")
        }

        after {
            SLog.debug("aft wc 1")
        }
    }

    whatHeCanSayMethod.hook {
        before {
            SLog.debug("bef wc 2")
        }

        after {
            SLog.debug("aft wc 2")
        }
    }

    whatHeCanSayMethod.hook {
        before {
            SLog.debug("bef wc 3")
        }

        before {
            SLog.debug("bef wc 3 - dup") // 会覆盖上一个
        }

        after {
            SLog.debug("aft wc 3")
        }
    }

    // K RESULT：h h2 d l l2-l2 l d h2 h

    kobeMethod.hook(SakikoHookPriority.LOWEST) {
        before {
            SLog.debug("bef k l")
        }

        after {
            SLog.debug("aft k l")
        }
    }

    kobeMethod.hook {
        before {
            SLog.debug("bef k d")
        }

        after {
            SLog.debug("aft k d")
        }
    }

    kobeMethod.hook(SakikoHookPriority.LOWEST) {
        before {
            SLog.debug("bef k l2")
        }

        after {
            SLog.debug("aft k l2")
        }
    }

    kobeMethod.hook(SakikoHookPriority.HIGHEST) {
        before {
            SLog.debug("bef k h")
        }

        after {
            SLog.debug("aft k h")
        }
    }

    kobeMethod.hook(SakikoHookPriority.HIGHEST) {
        before {
            SLog.debug("bef k h2")
        }

        after {
            SLog.debug("aft k h2")
        }
    }

    val man = Man()
    man.whatHeCanSay()
    man.kobe()
}

fun testHookUnhook() {
    val manClass = Man::class.resolve()
    val man = Man()

    SLog.debug("ORI：${man.whatHeCanSay()}")

    val whcsHandle = manClass.firstMethod { name = "whatHeCanSay" }.hook {
        replaceTo($$"NM$L")
    }

    SLog.debug("HOOKED：${man.whatHeCanSay()}")

    whcsHandle.remove()

    SLog.debug("UNHOOKED：${man.whatHeCanSay()}")
}*/

private const val TAG = "Test"

// 自定义断言
private fun assertEquals(expected: Any?, actual: Any?) {
    if (expected != actual) throw AssertionError("Expected: $expected, Actual: $actual")
}

private fun assertThrows(exceptionClass: Class<*>, block: () -> Unit) {
    try {
        block()
        throw AssertionError("Expected ${exceptionClass.simpleName} but nothing was thrown")
    } catch (e: Throwable) {
        if (!exceptionClass.isInstance(e)) throw AssertionError(
            "Expected ${exceptionClass.simpleName} but got ${e.javaClass.simpleName}",
            e
        )
    }
}

private fun assertTrue(condition: Boolean) {
    if (!condition) throw AssertionError("Expected true but got false")
}

private fun assertFalse(condition: Boolean) {
    if (condition) throw AssertionError("Expected false but got true")
}

private fun assertNull(value: Any?) {
    if (value != null) throw AssertionError("Expected null but got $value")
}

// 测试类
class DirectMethod {
    private fun add(a: Int, b: Int) = a + b

    class Test2Exception(message: String) : RuntimeException(message)

    private fun addFail(a: Int, b: Int): Int {
        throw Test2Exception("Test 2 Exception")
    }

    private fun sub(a: Int, b: Int) = a - b
}

class VirtualMethod {
    fun add(a: Int, b: Int) = a + b
}

class StaticMethod {
    companion object {
        @JvmStatic
        fun add(a: Int, b: Int) = a + b

        @JvmStatic
        fun primitiveArgsPassingTest(
            a: Int, b: Long, c: Float, d: Double,
            e: Boolean, f: Char, g: Byte, h: Short
        ) = "%d %d %.06f %.06f %b %c %d %d".format(a, b, c, d, e, f, g, h)
    }
}

class ConstructorI(val num: String)

class TestException(message: String) : RuntimeException(message)

class ReturnTypeTests {
    companion object {
        @JvmStatic
        fun returnInteger(a: Int) = a

        @JvmStatic
        fun returnLong(a: Long) = a

        @JvmStatic
        fun returnFloat(a: Float) = a

        @JvmStatic
        fun returnDouble(a: Double) = a

        @JvmStatic
        fun returnBoolean(a: Boolean) = a

        @JvmStatic
        fun returnChar(a: Char) = a

        @JvmStatic
        fun returnByte(a: Byte) = a

        @JvmStatic
        fun returnShort(a: Short) = a

        @JvmStatic
        fun returnVoid() {
        }
    }
}

private var sForGetClassStaticInitializerFired = false

private class ForGetClassStaticInitializer {
    companion object {
        init {
            sForGetClassStaticInitializerFired = true
        }
    }
}

private var sForHookClassStaticInitializerOriginalFired = 0
private var sForHookClassStaticInitializerHookedInvoked = 0

private class ForHookClassStaticInitializer {
    companion object {
        init {
            sForHookClassStaticInitializerOriginalFired++
        }
    }

    fun empty() {} // NOP
}

// 主测试入口
fun main() {
    initCore(true)

    test(::directMethod)
    test(::virtualMethod)
    test(::staticMethod)
    test(::constructor)
    test(::exceptionHandling1)
    test(::exceptionHandling2)
    test(::edgeCases)
    test(::multipleHooksOnSameMethod)
    test(::hookAfter)
    test(::multipleMethodsInSameClass)
    test(::primitiveArgsPassingTest)
    test(::returnTypeTests)
    test(::getClassStaticInitializer)
    test(::hookClassStaticInitializer)
}

fun test(testFun: () -> Unit) {
    SLog.info("TESTING：$testFun", TAG)

    runCatching(testFun)
        .onFailure { SLog.info("FAILED：$testFun\n${it.stackTraceToString()}", TAG) }
        .onSuccess { SLog.info("PASSED：$testFun", TAG) }
}

fun directMethod() {
    val method = DirectMethod::class.resolve().firstMethod { name = "add" }
    val invokable = method.of(DirectMethod())

    assertEquals(3, invokable.invoke(1, 2))

    val handle = method.hook {
        before {
            val a = args[0] as Int
            val b = args[1] as Int
            result = -a - b
        }
    }

    // File("hs_err_pid114514.log").writeBytes(SakiNative.getClassByteCode(DirectMethod::class.java))

    assertEquals(-3, invokable.invoke(1, 2))

    handle.remove()

    assertEquals(3, invokable.invoke(1, 2))
}

fun virtualMethod() {
    val method = VirtualMethod::class.resolve().firstMethod { name = "add" }
    val invokable = method.of(VirtualMethod())

    assertEquals(3, invokable.invoke(1, 2))

    val handle = method.hook {
        before {
            val a = args[0] as Int
            val b = args[1] as Int
            result = -a - b
        }
    }

    assertEquals(-3, invokable.invoke(1, 2))

    handle.remove()

    assertEquals(3, invokable.invoke(1, 2))
}

fun staticMethod() {
    val method = StaticMethod::class.resolve().firstMethod { name = "add" }

    assertEquals(3, method.invoke(1, 2))

    val handle = method.hook {
        before {
            val a = args[0] as Int
            val b = args[1] as Int
            result = -a - b
        }
    }

    assertEquals(-3, method.invoke(1, 2))

    handle.remove()

    assertEquals(3, method.invoke(1, 2))
}

fun constructor() {
    val clazz = ConstructorI::class.resolve()
    val ctor = clazz.firstConstructor {}

    assertEquals("主体", ctor.create("主体").num)

    val handle = ctor.hook { before { args[0] = "南下" } }

    // TIPS：是实例错了
    // FIXME：发现问题了，byd被hook（走恩情流程）的构造器弄了一个实例（导致null），而走原又创建了一个实例（南下）

    assertEquals("南下", ctor.create("恩情").num)

    handle.remove()

    assertEquals("你的盐我的醋", ctor.create("你的盐我的醋").num)
}

fun exceptionHandling1() {
    val method = DirectMethod::class.resolve().firstMethod { name = "add" }
    val invokable = method.of(DirectMethod())

    assertEquals(3, invokable.invoke(1, 2))

    val handle = method.hook { before { throwable = TestException("Test Exception") } }

    assertThrows(TestException::class.java) {
        runCatching { invokable.invoke(1, 2) }.onFailure { throw it.cause ?: it }
    }

    handle.remove()

    assertEquals(3, invokable.invoke(1, 2))
}

fun exceptionHandling2() {
    val method = DirectMethod::class.resolve().firstMethod { name = "addFail" }
    val invokable = method.of(DirectMethod())

    assertThrows(DirectMethod.Test2Exception::class.java) {
        runCatching { invokable.invoke(1, 2) }.onFailure { throw it.cause ?: it }
    }

    val handle = method.hook { before { result = 233 } }

    assertEquals(233, invokable.invoke(1, 2))

    handle.remove()

    assertThrows(DirectMethod.Test2Exception::class.java) {
        runCatching { invokable.invoke(1, 2) }.onFailure { throw it.cause ?: it }
    }
}

fun edgeCases() {
    val method = DirectMethod::class.resolve().firstMethod { name = "add" }
    val invokable = method.of(DirectMethod())

    assertEquals(0, invokable.invoke(0, 0))

    val handle = method.hook {
        before {
            val a = args[0] as Int
            val b = args[1] as Int
            result = -a - b
        }
    }

    assertEquals(0, invokable.invoke(0, 0))

    handle.remove()

    assertEquals(0, invokable.invoke(0, 0))
}

fun multipleHooksOnSameMethod() {
    val method = DirectMethod::class.resolve().firstMethod { name = "add" }
    val invokable = method.of(DirectMethod())

    assertEquals(3, invokable.invoke(1, 2))

    val handle1 = method.hook(SakikoHookPriority.HIGHEST) {
        before {
            val a = args[0] as Int
            val b = args[1] as Int
            result = a + b + 1
        }
    }

    val handle2 = method.hook(SakikoHookPriority.LOWEST) {
        before {
            val res = result as Int
            result = res * 2
        }
    }

    assertEquals(8, invokable.invoke(1, 2))

    handle1.remove()
    handle2.remove()

    assertEquals(3, invokable.invoke(1, 2))
}

fun hookAfter() {
    val method = DirectMethod::class.resolve().firstMethod { name = "add" }
    val invokable = method.of(DirectMethod())

    assertEquals(3, invokable.invoke(1, 2))

    val handle = method.hook {
        after {
            val res = result as Int
            result = res * 2
        }
    }

    assertEquals(6, invokable.invoke(1, 2))

    handle.remove()

    assertEquals(3, invokable.invoke(1, 2))
}

fun multipleMethodsInSameClass() {
    val addClass = DirectMethod::class.resolve()
    val addMethod = addClass.firstMethod { name = "add" }
    val subMethod = addClass.firstMethod { name = "sub" }
    val addInvokable = addMethod.of(DirectMethod())
    val subInvokable = subMethod.of(DirectMethod())

    assertEquals(3, addInvokable.invoke(1, 2))
    assertEquals(-1, subInvokable.invoke(1, 2))

    val handle1 = addMethod.hook {
        before {
            val a = args[0] as Int
            val b = args[1] as Int
            result = a + b + 1
        }
    }

    val handle2 = subMethod.hook {
        before {
            val a = args[0] as Int
            val b = args[1] as Int
            result = a - b - 1
        }
    }

    assertEquals(4, addInvokable.invoke(1, 2))
    assertEquals(-2, subInvokable.invoke(1, 2))

    handle1.remove()
    handle2.remove()

    assertEquals(3, addInvokable.invoke(1, 2))
    assertEquals(-1, subInvokable.invoke(1, 2))
}

fun primitiveArgsPassingTest() {
    val method = StaticMethod::class.resolve().firstMethod { name = "primitiveArgsPassingTest" }

    assertEquals(
        "1 2 3.000000 4.000000 true a 5 6",
        method.invoke(1, 2L, 3f, 4.0, true, 'a', 5.toByte(), 6.toShort())
    )

    val handle = method.hook {
        before {
            args[0] = (args[0] as Int) + 1
            args[1] = (args[1] as Long) + 1
            args[2] = (args[2] as Float) + 1
            args[3] = (args[3] as Double) + 1
            args[4] = !(args[4] as Boolean)
            args[5] = (args[5] as Char) + 1
            args[6] = ((args[6] as Byte) + 1).toByte()
            args[7] = ((args[7] as Short) + 1).toShort()
        }
    }

    assertEquals(
        "2 3 4.000000 5.000000 false b 6 7",
        method.invoke(1, 2L, 3f, 4.0, true, 'a', 5.toByte(), 6.toShort())
    )

    handle.remove()

    assertEquals(
        "1 2 3.000000 4.000000 true a 5 6",
        method.invoke(1, 2L, 3f, 4.0, true, 'a', 5.toByte(), 6.toShort())
    )
}

fun returnTypeTests() {
    val methodNames = arrayOf("returnInteger", "returnLong", "returnFloat", "returnDouble", "returnByte", "returnShort")
    val clazz = ReturnTypeTests::class.resolve()
    val illegal = IllegalStateException("Shouldn't happen!")

    methodNames.forEachIndexed { i, methodName ->
        val method = clazz.firstMethod { name = methodName }

        method.hook {
            after {
                val a = args[0]

                if (a is Number) result = when (a) {
                    is Int -> a + 1
                    is Long -> a + 1L
                    is Float -> a + 1f
                    is Double -> a + 1.0
                    is Byte -> (a + 1).toByte()
                    is Short -> (a + 1).toShort()
                    else -> throw illegal
                }
            }
        }

        val param = when (i) {
            0 -> 1
            1 -> 1L
            2 -> 1f
            3 -> 1.0
            4 -> 1.toByte()
            5 -> 1.toShort()
            else -> throw illegal
        }

        val returnValue = method.invoke(param)

        val doubleValue = (returnValue as Number).toDouble()
        if (abs(doubleValue - 2) > 0.0001) throw AssertionError("Expected value: 2, but got: $doubleValue")
    }

    val voidMethod = ReturnTypeTests::class.resolve().firstMethod { name = "returnVoid" }
    voidMethod.hook { after { SLog.debug("意味不明", TAG) } }

    voidMethod.invoke()
}

fun getClassStaticInitializer() {
    val clInit = MambaUtils.getClInitOrNull(ForGetClassStaticInitializer::class.java)!!

    assertEquals(ForGetClassStaticInitializer::class.java, clInit.declaringClass)
    assertTrue(Modifier.isStatic(clInit.modifiers))
    assertFalse(sForGetClassStaticInitializerFired) // 不要不小心把ClInit给调了

    assertNull(SakiNative.getClInitOrNull(IntArray::class.java))
    assertNull(SakiNative.getClInitOrNull(Long::class.javaPrimitiveType!!))
}

fun hookClassStaticInitializer() {
    val clInit = MambaUtils.getClInitOrNull(ForHookClassStaticInitializer::class.java)!!

    hookMember(clInit, HookConfig().apply {
        before {
            sForHookClassStaticInitializerHookedInvoked++
            result = null // force early return
        }
    })

    // 用来Call Clinit；Kotlin的`静态`方法不在自身，在伴生对象。所以要奇技淫巧
    ForHookClassStaticInitializer().empty()

    assertEquals(0, sForHookClassStaticInitializerOriginalFired)
    assertEquals(1, sForHookClassStaticInitializerHookedInvoked)
}
