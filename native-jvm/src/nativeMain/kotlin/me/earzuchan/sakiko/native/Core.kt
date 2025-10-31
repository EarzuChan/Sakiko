@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package me.earzuchan.sakiko.native

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.cinterop.*
import kotlinx.cinterop.allocArrayOf
import libjava.*
import me.earzuchan.sakiko.native.models.JObjectStorage
import me.earzuchan.sakiko.native.utils.JniUtils.getIfHasException
import me.earzuchan.sakiko.native.utils.JniUtils.getJvmti
import me.earzuchan.sakiko.native.utils.JniUtils.getEnv
import me.earzuchan.sakiko.native.utils.JniUtils.getClassNameOf
import me.earzuchan.sakiko.native.utils.JniUtils.getByteCodeBy
import me.earzuchan.sakiko.native.utils.JniUtils.storeJObject
import me.earzuchan.sakiko.native.utils.JniUtils.toJByteArray
import me.earzuchan.sakiko.native.utils.Log
import kotlin.experimental.ExperimentalNativeApi

val transforming = SynchronizedObject()
val mapManipulating = SynchronizedObject()

// 能用
@CName("Java_me_earzuchan_sakiko_core_SakiNative_getClassByteCode")
fun getClassByteCode(env: CPointer<JNIEnvVar>, jc: jclass, targetClass: jclass?): jobject {
    if (targetClass == null) throw IllegalArgumentException("目标类为null")

    val jtI = jvmti!!.pointed.pointed!!
    val eI = env.pointed.pointed!!

    var byteCode = byteArrayOf()

    synchronized(transforming) {
        synchronized(mapManipulating) { classFileBytes.clear() }
        try {
            // 开启hook
            check(
                jtI.SetEventNotificationMode!!(
                    jvmti, JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, null
                ) == JVMTI_ERROR_NONE
            ) { "开启JVMTI事件回调失败" }

            memScoped {
                val classes = allocArrayOf(targetClass)
                check(jtI.RetransformClasses!!(jvmti, 1, classes) == JVMTI_ERROR_NONE) { "请求重新转换类失败" }
            }

            // 获取字节码
            synchronized(mapManipulating) {
                byteCode = classFileBytes.firstNotNullOf { (name, data) ->
                    if (name.isValid() && eI.IsSameObject!!(env, name.peek(), targetClass) == 1.toUByte()) data
                    else null
                }
            }

            check(byteCode.isNotEmpty()) { "未获取到字节码" }
        } finally {
            // 必须执行关闭
            jtI.SetEventNotificationMode!!(
                jvmti, JVMTI_DISABLE,
                JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, null
            )
            synchronized(mapManipulating) { classFileBytes.clear() }
        }
    }

    check(!env.getIfHasException()) { "JNI异常发生" }
    return env.toJByteArray(byteCode)
}

// 能用
@CName("Java_me_earzuchan_sakiko_core_SakiNative_redefineClass")
fun redefineClass(
    env: CPointer<JNIEnvVar>, jc: jclass,
    targetClass: jclass, byteCodeJ: jbyteArray,
    shouldBypassVerification: jboolean // 这个先不理
) {
    val TAG = "RedefineClass"
    val byteCode = env.getByteCodeBy(byteCodeJ)
    Log.d(TAG, "成功转为本地，大小：${byteCode.size}")
    check(!env.getIfHasException()) { "JNI异常发生" }

    val clzName = env.getClassNameOf(targetClass).also {
        Log.d(TAG, "成功取得类名：$it")
    }.replace('.', '/')

    byteCode.usePinned { pinned ->
        memScoped {
            val classDefinition = cValue<jvmtiClassDefinition> {
                klass = targetClass
                class_byte_count = byteCode.size
                class_bytes = pinned.addressOf(0).reinterpret()
            }
            Log.d(TAG, "成功创建类定义")

            // TODO：这块本是Bypass校验

            check(
                jvmti!!.pointed.pointed!!.RedefineClasses!!(
                    jvmti, 1, classDefinition.ptr
                ) == JVMTI_ERROR_NONE
            ) { "调用重定义类失败" }
            Log.d(TAG, "成功重定义类")
        }

        // TODO：恢复校验
    }
}

// 测试

data class TestClz(val man: Int)

var nun = -1
var mamba: TestClz? = null
var ao: TestClz = TestClz(1919)

@CName("Java_me_earzuchan_sakiko_core_SakiNative_test1")
fun test1(env: CPointer<JNIEnvVar>, jc: jclass) {
    val TAG = "Test1"

    Log.i(TAG, "ao：${ao.man}")

    nun = 111
    mamba = TestClz(114514)
    ao = TestClz(810)

    Log.i(TAG, "nun：$nun；mamba：${mamba!!.man}；ao：${ao.man}")
}

@CName("Java_me_earzuchan_sakiko_core_SakiNative_test2")
fun test2(env: CPointer<JNIEnvVar>, jc: jclass) {
    val TAG = "Test2"

    Log.i(TAG, "nun：$nun；mamba：${mamba!!.man}；ao：${ao.man}")
}

val classFileBytes = hashMapOf<JObjectStorage, ByteArray>()

fun onJvmtiGetClassFile(
    jniEnv: CPointer<JNIEnvVar>, classBeingRedefined: jclass?,
    classDataLen: Int, classData: CPointer<UByteVar>
) {
    val TAG = "OnJvmtiGetClassFile"

    if (classBeingRedefined == null) Log.i(TAG, "这这不弄")
    else {
        Log.i(TAG, "接到类重定义：${jniEnv.getClassNameOf(classBeingRedefined)}")

        val classFile = classData.readBytes(classDataLen)
        val obj = jniEnv.storeJObject(classBeingRedefined)  // 保存全局引用

        classFileBytes[obj] = classFile
    }
}

// 初始化

@CName("JNI_OnLoad")
fun jniOnLoad(vm: CPointer<JavaVMVar>): jint {
    val TAG = "JniOnLoad"

    val env = vm.getEnv()
    Log.i(TAG, "JNI正常，申请版本：1.6")

    setupJvmti(vm)
    Log.i(TAG, "JVMTI正常：1.2，已好一顿设")
    if (env.getIfHasException()) throw IllegalStateException("补药啊")

    return JNI_VERSION_1_6
}

var jvmti: CPointer<jvmtiEnvVar>? = null

private fun setupJvmti(vm: CPointer<JavaVMVar>) = memScoped {
    val TAG = "SetupJvmti"

    jvmti = vm.getJvmti()
    Log.i(TAG, "获取JVMTI成功")

    val jvmtiInterface = jvmti!!.pointed.pointed!!

    val capabilities = cValue<jvmtiCapabilities> {
        can_get_bytecodes = 1u
        can_get_constant_pool = 1u
        can_redefine_classes = 1u
        can_retransform_classes = 1u
        can_redefine_any_class = 1u
        can_retransform_any_class = 1u
    }

    if (jvmtiInterface.AddCapabilities!!(
            jvmti,
            capabilities.ptr
        ) != JVMTI_ERROR_NONE
    ) throw IllegalStateException("添加权能不成功")
    Log.i(TAG, "创建并添加权能成功")

    val callbacks = cValue<jvmtiEventCallbacks> {
        ClassFileLoadHook =
            staticCFunction { _: CPointer<jvmtiEnvVar>,
                              jniEnv: CPointer<JNIEnvVar>,
                              classBeingRedefined: jclass?,
                              _: jobject,
                              _: CPointer<ByteVar>,
                              _: jobject,
                              classDataLen: Int,
                              classData: CPointer<UByteVar>,
                              _: CPointer<IntVar>,
                              _: CPointer<CPointerVar<UByteVar>> ->
                onJvmtiGetClassFile(jniEnv, classBeingRedefined, classDataLen, classData)
            } as jvmtiEventClassFileLoadHook
    }

    if (jvmtiInterface.SetEventCallbacks!!(
            jvmti,
            callbacks.ptr,
            sizeOf<jvmtiEventCallbacks>().toInt()
        ) != JVMTI_ERROR_NONE
    ) throw IllegalStateException("添加回调不成功")
    Log.i(TAG, "创建并添加回调成功")
}