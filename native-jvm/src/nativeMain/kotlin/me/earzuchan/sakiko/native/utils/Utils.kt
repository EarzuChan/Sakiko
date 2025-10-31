package me.earzuchan.sakiko.native.utils

import kotlinx.cinterop.*
import libjava.*
import me.earzuchan.sakiko.native.models.JObjectStorage

object Log {
    fun d(tag: String, msg: String) = println("[D] $tag > $msg")

    fun e(tag: String, msg: String) = println("[E] $tag > $msg")
    fun e(tag: String, msg: String, e: Throwable) = println("[E] $tag > $msg\n${e.stackTraceToString()}")

    fun i(tag: String, msg: String) = println("[I] $tag > $msg")
    fun w(tag: String, msg: String) = println("[W] $tag > $msg")
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

    fun CPointer<JavaVMVar>.getJvmti(): CPointer<jvmtiEnvVar> = memScoped {
        val vmValue = this@getJvmti.pointed.pointed!!

        val jvmtiStorage = alloc<CPointerVar<jvmtiEnvVar>>()
        val result = vmValue.GetEnv!!(this@getJvmti, jvmtiStorage.ptr.reinterpret(), JVMTI_VERSION_1_2.toInt())

        check(result == JNI_OK) { "获取JVMTI不成功" }
        // else Log.i(TAG, "获取JVMTI的PtrVar成功")

        val theOne = jvmtiStorage.value ?: throw NullPointerException("何意味")
        // Log.i(TAG, "JVMTI的PtrVar转换成Ptr成功")

        return theOne
    }

    fun CPointer<JNIEnvVar>.getIfHasException(): Boolean = pointed.pointed!!.ExceptionCheck!!(this) == 1.toUByte()

    val Boolean.j: UByte get() = if (this) 1u else 0u

    // 定义一个接受 JNIEnv* 和 jstring 的 native 函数
    fun CPointer<JNIEnvVar>.getString(jStr: jstring?): String? {
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

    fun CPointer<JNIEnvVar>.getFloatArray(jArr: jfloatArray?): FloatArray {
        // 处理 null 输入，返回一个空数组，这比返回 null 更安全，避免了调用方的空检查
        val nonNullJArr = jArr ?: return floatArrayOf().also {
            Log.w(TAG, "Input jfloatArray was null, returning empty array")
        }

        // 获取实际的 JNIEnv 结构体指针
        val realEnv = pointed.pointed!!

        // 调用 JNI 函数获取指向数组元素的 C 指针
        // 第三个参数(isCopy)在这里我们不关心，传入 null 即可

        // 失败时返回空数组
        val elementsPtr =
            realEnv.GetFloatArrayElements!!.invoke(this, nonNullJArr, null) ?: return floatArrayOf().also {
                Log.e(
                    TAG,
                    "JNI GetFloatArrayElements failed to get pointer"
                )
            }

        // 检查 JNI 调用是否失败 (例如，内存不足)
        // 使用 try...finally 确保资源总是被释放
        try {
            // 获取数组的长度
            val length = realEnv.GetArrayLength!!.invoke(this, nonNullJArr)
            if (length == 0) return floatArrayOf()

            // 创建一个 Kotlin FloatArray
            val kotlinArray = FloatArray(length)
            // 将数据从 C 指针复制到 Kotlin 数组
            for (i in 0 until length) kotlinArray[i] = elementsPtr[i]

            return kotlinArray
        } finally {
            //释放 C 指针
            // 因为我们只是读取数据，没有做任何修改，所以使用 JNI_ABORT 是最高效的。
            // 它告诉 JVM：“我没有修改任何东西，请直接释放内存，无需将数据复制回去。”
            realEnv.ReleaseFloatArrayElements!!.invoke(this, nonNullJArr, elementsPtr, JNI_ABORT)
        }
    }

    fun CPointer<JNIEnvVar>.storeJObject(obj: jobject) = JObjectStorage(this, obj)

    fun CPointer<JNIEnvVar>.toJByteArray(byteArray: ByteArray): jbyteArray {
        val rE = pointed.pointed!!
        val jArr = rE.NewByteArray!!(this, byteArray.size) ?: throw IllegalStateException("要数组，ENV给了个NullPtr")

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
                ?: throw IllegalStateException("不有GetName方法")
        realEnv.DeleteLocalRef!!(this@getClassNameOf, mamba)

        val nameJ = realEnv.CallObjectMethodA!!(this@getClassNameOf, clz, getName, null) as jstring
        if (getIfHasException()) throw IllegalStateException("打瓦喊妈妈")

        getString(nameJ) ?: throw NullPointerException("坠机了")
    }
}