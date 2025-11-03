package me.earzuchan.sakiko.native.utils

import kotlinx.cinterop.*
import kotlinx.cinterop.ExperimentalForeignApi
import platform.android.*
import platform.android.JNI_OK
import platform.android.JNI_VERSION_1_6

object Log {
    fun d(tag: String, msg: String) = __android_log_print(ANDROID_LOG_DEBUG.toInt(), tag, msg)

    fun e(tag: String, msg: String) = __android_log_print(ANDROID_LOG_ERROR.toInt(), tag, msg)
    fun e(tag: String, msg: String, e: Throwable) =
        __android_log_print(ANDROID_LOG_ERROR.toInt(), tag, "$msg\n${e.stackTraceToString()}")

    fun i(tag: String, msg: String) = __android_log_print(ANDROID_LOG_INFO.toInt(), tag, msg)
    fun w(tag: String, msg: String) = __android_log_print(ANDROID_LOG_WARN.toInt(), tag, msg)
}

@OptIn(ExperimentalForeignApi::class)
object JniUtils {
    private const val TAG = "JniUtils"

    fun CPointer<JavaVMVar>.getEnv(): CPointer<JNIEnvVar> = memScoped {
        val vmValue = this@getEnv.pointed.pointed!!

        val envStorage = alloc<CPointerVar<JNIEnvVar>>()
        val result = vmValue.GetEnv!!(this@getEnv, envStorage.ptr.reinterpret(), JNI_VERSION_1_6)

        check(result == JNI_OK) { "JNI初始化不成功" }

        val theOne = envStorage.value ?: throw NullPointerException("何意味")

        return theOne
    }

    fun CPointer<JNIEnvVar>.hasException(): Boolean = pointed.pointed!!.ExceptionCheck!!(this) == 1.toUByte()

    val Boolean.j: UByte get() = if (this) 1u else 0u

    val jboolean.k: Boolean get() = this == 1.toUByte()

    // 定义一个接受 JNIEnv* 和 jstring 的 native 函数
    fun CPointer<JNIEnvVar>.getStringBy(jStr: jstring?): String? {
        val nonNullJStr = jStr ?: return null.also { Log.w(TAG, "Input jStr was null") }
        val realEnv = pointed.pointed!!

        val utfChars = realEnv.GetStringUTFChars!!(this, nonNullJStr, null) ?: return null.also {
            Log.e(TAG, "GetStrUTF failed to return a valid pointer")
        }


        // 在 try 块中，安全地将 C 字符串转换为 Kotlin 字符串。
        val kStr = runCatching { utfChars.toKStringFromUtf8() }.onFailure {
            // 如果转换失败，记录详细的异常信息。
            Log.e(TAG, "Failed to convert UTF8 cStr to kStr", it)
        }.getOrNull()

        realEnv.ReleaseStringUTFChars!!(this, nonNullJStr, utfChars)

        return kStr
    }

    fun CPointer<JNIEnvVar>.getByteArrayBy(jArr: jbyteArray?): ByteArray {
        // 处理 null 输入，返回一个空数组，这比返回 null 更安全，避免了调用方的空检查
        val nonNullJArr = jArr ?: return byteArrayOf().also {
            Log.w(TAG, "Input jfloatArray was null, returning empty array")
        }

        // 获取实际的 JNIEnv 结构体指针
        val realEnv = pointed.pointed!!

        // 调用 JNI 函数获取指向数组元素的 C 指针
        // 第三个参数(isCopy)在这里我们不关心，传入 null 即可

        // 失败时返回空数组
        val elementsPtr =
            realEnv.GetByteArrayElements!!.invoke(this, nonNullJArr, null) ?: return byteArrayOf().also {
                Log.e(TAG, "JNI GetFloatArrayElements failed to get pointer")
            }

        // 检查 JNI 调用是否失败 (例如，内存不足)
        // 使用 try...finally 确保资源总是被释放
        try {
            // 获取数组的长度
            val length = realEnv.GetArrayLength!!.invoke(this, nonNullJArr)
            if (length == 0) return byteArrayOf()

            // 创建一个 Kotlin Array
            val kotlinArray = ByteArray(length)
            // 将数据从 C 指针复制到 Kotlin 数组
            for (i in 0 until length) kotlinArray[i] = elementsPtr[i]

            return kotlinArray
        } finally {
            //释放 C 指针
            realEnv.ReleaseByteArrayElements!!.invoke(this, nonNullJArr, elementsPtr, JNI_ABORT)
        }
    }

    fun CPointer<JNIEnvVar>.toJByteArray(byteArray: ByteArray): jbyteArray {
        val rE = pointed.pointed!!
        val jArr = rE.NewByteArray!!(this, byteArray.size) ?: error("要数组，ENV给了个NullPtr")

        byteArray.usePinned {
            rE.SetByteArrayRegion!!(this, jArr, 0, byteArray.size, it.addressOf(0).reinterpret())
        }

        return jArr
    }

    fun CPointer<JNIEnvVar>.getClassNameOf(clz: jclass): String = memScoped {
        val realEnv = pointed.pointed!!

        val mamba = realEnv.FindClass!!(this@getClassNameOf, "java/lang/Class".cstr.ptr)
        val getName =
            realEnv.GetMethodID!!(this@getClassNameOf, mamba, "getName".cstr.ptr, "()Ljava/lang/String;".cstr.ptr)
                ?: error("不有GetName方法")
        realEnv.DeleteLocalRef!!(this@getClassNameOf, mamba)

        val nameJ = realEnv.CallObjectMethodA!!(this@getClassNameOf, clz, getName, null) as jstring
        if (hasException()) error("打瓦喊妈妈")

        getStringBy(nameJ) ?: throw NullPointerException("坠机了")
    }

    fun CPointer<JNIEnvVar>.unwrap(ori: jobject, to: jvalue, by: Char) = memScoped {
        val jni = pointed.pointed!!

        fun illegal(): Nothing = error("找不到好方法捏")

        when (by) {
            'Z' -> {
                val kBoolean = jni.FindClass!!(this@unwrap, "java/lang/Boolean".cstr.ptr)
                val cid = jni.GetMethodID!!(this@unwrap, kBoolean, "booleanValue".cstr.ptr, "()Z".cstr.ptr) ?: illegal()
                to.z = jni.CallBooleanMethodA!!(this@unwrap, ori, cid, null)
            }

            'B' -> {
                val kByte = jni.FindClass!!(this@unwrap, "java/lang/Byte".cstr.ptr)
                val cid = jni.GetMethodID!!(this@unwrap, kByte, "byteValue".cstr.ptr, "()B".cstr.ptr) ?: illegal()
                to.b = jni.CallByteMethodA!!(this@unwrap, ori, cid, null)
            }

            'C' -> {
                val kChar = jni.FindClass!!(this@unwrap, "java/lang/Character".cstr.ptr)
                val cid = jni.GetMethodID!!(this@unwrap, kChar, "charValue".cstr.ptr, "()C".cstr.ptr) ?: illegal()
                to.c = jni.CallCharMethodA!!(this@unwrap, ori, cid, null)
            }

            'S' -> {
                val kShort = jni.FindClass!!(this@unwrap, "java/lang/Short".cstr.ptr)
                val cid = jni.GetMethodID!!(this@unwrap, kShort, "shortValue".cstr.ptr, "()S".cstr.ptr) ?: illegal()
                to.s = jni.CallShortMethodA!!(this@unwrap, ori, cid, null)
            }

            'I' -> {
                val kInt = jni.FindClass!!(this@unwrap, "java/lang/Integer".cstr.ptr)
                val cid = jni.GetMethodID!!(this@unwrap, kInt, "intValue".cstr.ptr, "()I".cstr.ptr) ?: illegal()
                to.i = jni.CallIntMethodA!!(this@unwrap, ori, cid, null)
            }

            'J' -> {
                val kLong = jni.FindClass!!(this@unwrap, "java/lang/Long".cstr.ptr)
                val cid = jni.GetMethodID!!(this@unwrap, kLong, "longValue".cstr.ptr, "()J".cstr.ptr) ?: illegal()
                to.j = jni.CallLongMethodA!!(this@unwrap, ori, cid, null)
            }

            'F' -> {
                val kFloat = jni.FindClass!!(this@unwrap, "java/lang/Float".cstr.ptr)
                val cid = jni.GetMethodID!!(this@unwrap, kFloat, "floatValue".cstr.ptr, "()F".cstr.ptr) ?: illegal()
                to.f = jni.CallFloatMethodA!!(this@unwrap, ori, cid, null)
            }

            'D' -> {
                val kDouble = jni.FindClass!!(this@unwrap, "java/lang/Double".cstr.ptr)
                val cid = jni.GetMethodID!!(this@unwrap, kDouble, "doubleValue".cstr.ptr, "()D".cstr.ptr) ?: illegal()
                to.d = jni.CallDoubleMethodA!!(this@unwrap, ori, cid, null)
            }

            'V' -> to.l = null

            'L' -> to.l = ori

            else -> error("妈的：$by")
        }
    }

    fun CPointer<JNIEnvVar>.unwrap(ori: jobject, by: Char): jvalue = memScoped {
        return alloc<jvalue>().also { unwrap(ori, it, by) }
    }

    fun CPointer<JNIEnvVar>.wrap(ori: jvalue, by: Char): jobject? = memScoped {
        val jni = pointed.pointed!!

        return when (by) {
            'Z' -> {
                val kBoolean = jni.FindClass!!(this@wrap, "java/lang/Boolean".cstr.ptr)

                val cid =
                    jni.GetStaticMethodID!!(this@wrap, kBoolean, "valueOf".cstr.ptr, "(Z)Ljava/lang/Boolean;".cstr.ptr)

                jni.CallStaticObjectMethodA!!(this@wrap, kBoolean, cid, ori.ptr)
            }

            'B' -> {
                val kByte = jni.FindClass!!(this@wrap, "java/lang/Byte".cstr.ptr)

                val cid = jni.GetStaticMethodID!!(this@wrap, kByte, "valueOf".cstr.ptr, "(B)Ljava/lang/Byte;".cstr.ptr)

                jni.CallStaticObjectMethodA!!(this@wrap, kByte, cid, ori.ptr)
            }

            'C' -> {
                val kChar = jni.FindClass!!(this@wrap, "java/lang/Character".cstr.ptr)

                val cid =
                    jni.GetStaticMethodID!!(this@wrap, kChar, "valueOf".cstr.ptr, "(C)Ljava/lang/Character;".cstr.ptr)

                jni.CallStaticObjectMethodA!!(this@wrap, kChar, cid, ori.ptr)
            }

            'S' -> {
                val kShort = jni.FindClass!!(this@wrap, "java/lang/Short".cstr.ptr)

                val cid =
                    jni.GetStaticMethodID!!(this@wrap, kShort, "valueOf".cstr.ptr, "(S)Ljava/lang/Short;".cstr.ptr)

                jni.CallStaticObjectMethodA!!(this@wrap, kShort, cid, ori.ptr)
            }

            'I' -> {
                val kInt = jni.FindClass!!(this@wrap, "java/lang/Integer".cstr.ptr)

                val cid =
                    jni.GetStaticMethodID!!(this@wrap, kInt, "valueOf".cstr.ptr, "(I)Ljava/lang/Integer;".cstr.ptr)

                jni.CallStaticObjectMethodA!!(this@wrap, kInt, cid, ori.ptr)
            }

            'J' -> {
                val kLong = jni.FindClass!!(this@wrap, "java/lang/Long".cstr.ptr)

                val cid =
                    jni.GetStaticMethodID!!(this@wrap, kLong, "valueOf".cstr.ptr, "(J)Ljava/lang/Long;".cstr.ptr)

                jni.CallStaticObjectMethodA!!(this@wrap, kLong, cid, ori.ptr)
            }

            'F' -> {
                val kFloat = jni.FindClass!!(this@wrap, "java/lang/Float".cstr.ptr)

                val cid =
                    jni.GetStaticMethodID!!(this@wrap, kFloat, "valueOf".cstr.ptr, "(F)Ljava/lang/Float;".cstr.ptr)

                jni.CallStaticObjectMethodA!!(this@wrap, kFloat, cid, ori.ptr)
            }

            'D' -> {
                val kDouble = jni.FindClass!!(this@wrap, "java/lang/Double".cstr.ptr)

                val cid =
                    jni.GetStaticMethodID!!(this@wrap, kDouble, "valueOf".cstr.ptr, "(D)Ljava/lang/Double;".cstr.ptr)

                jni.CallStaticObjectMethodA!!(this@wrap, kDouble, cid, ori.ptr)
            }

            'V' -> null

            'L' -> ori.l

            else -> error("妈的：$by")
        }
    }
}