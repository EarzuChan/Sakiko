package me.earzuchan.sakiko.test

import me.earzuchan.sakiko.api.reflecting.method
import me.earzuchan.sakiko.api.utils.Log
import org.junit.jupiter.api.Assertions.assertEquals

const val TAG = "SakikoTestMain"

object TestMain {
    @JvmStatic
    fun main(args: Array<String>) {
        Log.info(TAG, "Hello Sakiko, Let's rock!")

        val testMethodClass = MethodsForTest::class.java

        Log.info(TAG, "First")
        assertEquals("kobe man", MethodsForTest.method1("man"))
        testMethodClass.method {
            name = "method1"
        }.hook {
            before {
                result = "mamba"
            }
        }
    }
}

object MethodsForTest {
    @JvmStatic
    fun method1(man: String): String {
        return "kobe $man"
    }
}