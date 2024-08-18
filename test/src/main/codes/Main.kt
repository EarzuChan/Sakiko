package me.earzuchan.sakiko.test

import me.earzuchan.sakiko.api.utils.Log

const val TAG = "SakikoTestMain"

object Main {
    @JvmStatic
    fun main(args: Array<String>?) {
        Log.info(TAG, "Main")

        Log.info(TAG, "Test: ${test("baby")}")
    }

    @JvmStatic
    fun test(man: String): String {
        return "test $man"
    }
}