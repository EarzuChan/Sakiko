import com.highcapable.kavaref.KavaRef.Companion.resolve
import me.earzuchan.sakiko.api.hook.SakikoHookPriority
import me.earzuchan.sakiko.api.hook.hook
import me.earzuchan.sakiko.api.utils.SLog
import kotlin.math.abs

private const val TAG = "UnitTest"

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

class MultiAndPriority {
    fun whatHeCanSay() = "Mamba out"
    fun kobe() = 111
}

// 主测试入口
fun performUnitTests() {
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
    test(::multiAndPriority)
    test(::primitiveArgsPassingTest)
    test(::returnTypeTests)
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
    // CHECK：在Yuki，应该是Throwable.throwToApp()

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

    // 原来是在Before，可这是违反Xposed设计的
    val handle1 = method.hook(SakikoHookPriority.LOWEST) {
        after {
            SLog.debug("应该1")
            val a = args[0] as Int
            val b = args[1] as Int
            result = a + b + 1
        }
    }

    val handle2 = method.hook(SakikoHookPriority.HIGHEST) {
        after {
            SLog.debug("应该2")
            val res = result as Int
            result = res * 2
        }
    }

    assertEquals(8, invokable.invoke(1, 2))

    handle1.remove()
    handle2.remove()

    assertEquals(3, invokable.invoke(1, 2))
}

// TODO：应该再测试一下犯些禁忌，比如Hook内部类和不允许的类

// CHECK：唯一就是安卓上顺序不对，但原生LSP的Yuki行为良好，是AliuHook的问题罢
fun multiAndPriority() {
    val manClass = MultiAndPriority::class.resolve()

    val whatHeCanSayMethod = manClass.firstMethod { name = "whatHeCanSay" }
    val kobeMethod = manClass.firstMethod { name = "kobe" }

    // WCS RESULT：123dup321

    val strB1 = StringBuilder()
    whatHeCanSayMethod.hook {
        before {
            SLog.debug("bef wc 1")
            strB1.append("1")
        }

        after {
            SLog.debug("aft wc 1")
            strB1.append("1")
        }
    }

    whatHeCanSayMethod.hook {
        before {
            SLog.debug("bef wc 2")
            strB1.append("2")
        }

        after {
            SLog.debug("aft wc 2")
            strB1.append("2")
        }
    }

    whatHeCanSayMethod.hook {
        before {
            SLog.debug("bef wc 3")
            strB1.append("3")
        }

        before {
            SLog.debug("bef wc 3dup") // 会覆盖上一个
            strB1.append("3dup")
        }

        after {
            SLog.debug("aft wc 3")
            strB1.append("3")
        }
    }

    // K RESULT：hh2dll2l2ldh2h

    val strB2 = StringBuilder()
    kobeMethod.hook(SakikoHookPriority.LOWEST) {
        before {
            SLog.debug("bef k l")
            strB2.append("l")
        }

        after {
            SLog.debug("aft k l")
            strB2.append("l")
        }
    }

    kobeMethod.hook {
        before {
            SLog.debug("bef k d")
            strB2.append("d")
        }

        after {
            SLog.debug("aft k d")
            strB2.append("d")
        }
    }

    kobeMethod.hook(SakikoHookPriority.LOWEST) {
        before {
            SLog.debug("bef k l2")
            strB2.append("l2")
        }

        after {
            SLog.debug("aft k l2")
            strB2.append("l2")
        }
    }

    kobeMethod.hook(SakikoHookPriority.HIGHEST) {
        before {
            SLog.debug("bef k h")
            strB2.append("h")
        }

        after {
            SLog.debug("aft k h")
            strB2.append("h")
        }
    }

    kobeMethod.hook(SakikoHookPriority.HIGHEST) {
        before {
            SLog.debug("bef k h2")
            strB2.append("h2")
        }

        after {
            SLog.debug("aft k h2")
            strB2.append("h2")
        }
    }

    val man = MultiAndPriority()
    man.whatHeCanSay()
    man.kobe()

    assertEquals("123dup321", strB1.toString())
    assertEquals("hh2dll2l2ldh2h", strB2.toString())
}

// TODO：Call Ori

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

// FIXME：为啥这里也安卓非预期结果
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

// 因为RefX是Core独享的，故暂无法测试ClInit的Hook了