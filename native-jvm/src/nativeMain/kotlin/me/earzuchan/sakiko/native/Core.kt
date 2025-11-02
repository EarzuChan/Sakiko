@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package me.earzuchan.sakiko.native

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.cinterop.*
import kotlinx.cinterop.allocArrayOf
import libjava.*
import me.earzuchan.sakiko.native.models.JObjectStorage
import me.earzuchan.sakiko.native.utils.JniUtils.hasException
import me.earzuchan.sakiko.native.utils.JniUtils.getJvmti
import me.earzuchan.sakiko.native.utils.JniUtils.getEnv
import me.earzuchan.sakiko.native.utils.JniUtils.getClassNameOf
import me.earzuchan.sakiko.native.utils.JniUtils.getByteArrayBy
import me.earzuchan.sakiko.native.utils.JniUtils.getStringBy
import me.earzuchan.sakiko.native.utils.JniUtils.k
import me.earzuchan.sakiko.native.utils.JniUtils.storeJObject
import me.earzuchan.sakiko.native.utils.JniUtils.toJByteArray
import me.earzuchan.sakiko.native.utils.JniUtils.unwrap
import me.earzuchan.sakiko.native.utils.JniUtils.wrap
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

    check(!env.hasException()) { "JNI异常发生" }
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
    val byteCode = env.getByteArrayBy(byteCodeJ)
    Log.d(TAG, "这位置不错，大小：${byteCode.size}")
    check(!env.hasException()) { "JNI异常发生" }

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
            Log.d(TAG, "成功重定义类，想Hook是吧，我奉陪")
        }

        // TODO：恢复校验
    }
}

@CName("Java_me_earzuchan_sakiko_core_SakiNative_getClInitOrNull")
fun getClInitOrNull(env: CPointer<JNIEnvVar>, jc: jclass, targetClass: jclass): jobject? {
    val TAG = "GetClassInitializer"

    val jvmtiInterface = jvmti!!.pointed.pointed!!
    val envInterface = env.pointed.pointed!!
    val className = env.getClassNameOf(targetClass)

    memScoped {
        fun deallocate(ptr: CPointer<*>?) {
            ptr?.let { jvmtiInterface.Deallocate!!(jvmti!!, it.reinterpret()) }
        }

        val countRef = alloc<IntVar>()
        val methodsRef = alloc<CPointerVar<jmethodIDVar>>() // CHECK：也许是得换成cArray或者是cValues？

        // CHECK：何意为！这不该失败
        val rc = jvmtiInterface.GetClassMethods!!(jvmti, targetClass, countRef.ptr, methodsRef.ptr)
        if (rc != JVMTI_ERROR_NONE) error("拨弄不到啊一个一个方法们：$className；情况：$rc")

        val methods = methodsRef.value ?: return null.also { Log.w(TAG, "方法数组是空指针捏：$className") }
        Log.d(TAG, "拨弄到一个一个方法们：$className")

        var clInit: jmethodID? = null

        for (i in 0 until countRef.value) {
            val nameRef = alloc<CPointerVar<ByteVar>>()
            val signRef = alloc<CPointerVar<ByteVar>>()

            // 我们不需要通用，故跳过本次
            if (jvmtiInterface.GetMethodName!!(
                    jvmti!!, methods[i], nameRef.ptr, signRef.ptr, null
                ) != JVMTI_ERROR_NONE
            ) continue

            val name = nameRef.value?.toKString()
            val signature = signRef.value?.toKString()
            Log.d(TAG, "拨弄到非通用捏；名字：$name；签名：$signature")

            if (name == "<clinit>" && signature == "()V") clInit = methods[i]

            deallocate(nameRef.value)
            deallocate(signRef.value)

            if (clInit != null) break
        }

        val nothing = clInit == null
        Log.d(TAG, "拨弄一个一个方法终了，无<clinit>吗：$nothing")

        deallocate(methods)

        if (nothing) return null

        return envInterface.ToReflectedMethod!!(env, targetClass, clInit, JNI_TRUE.toUByte())!!
    }
}

// TIPS：参数数检查不要丢给Native
@CName("Java_me_earzuchan_sakiko_core_SakiNative_proInvoke")
fun proInvoke(
    env: CPointer<JNIEnvVar>, jc: jclass,
    man: jobject, specSignJ: jstring,
    manDeclareClz: jclass, isStaticJ: jboolean,
    instance: jobject?, args: jobjectArray
): jobject? = memScoped {
    val TAG = "ProInvoke"
    val jni = env.pointed.pointed!!

    // 获取 methodID
    val methodId = jni.FromReflectedMethod!!(env, man) ?: error("获取MethodId失败")
    Log.d(TAG, "获取MethodId成功")

    // 获取特签
    val specSign = env.getStringBy(specSignJ) ?: error("拨弄到方法签名失败")
    Log.d(TAG, "获取特签成功：$specSign")

    // 解析特签里的参数类型
    val (paramStr, returnStr) = specSign.split(',')
    val paramShorts = paramStr.toList()
    val returnTypeShort = returnStr[0]
    Log.d(TAG, "特签，令人沉醉")

    val isStatic = isStaticJ.k
    Log.d(TAG, "静态：$isStatic；实例非空：${instance != null}")

    // Unwrap参数并构建数组
    val argCount = paramShorts.size
    val cJArgs = allocArray<jvalue>(argCount) {
        val arg = jni.GetObjectArrayElement!!(env, args, it)!!
        Log.d(TAG,"取得第${it+1}")

        env.unwrap(arg, this@allocArray, paramShorts[it])
        Log.d(TAG,"Unwrap第${it+1}")

        if (env.hasException()) error("妈咪何以")
    }
    Log.d(TAG, "Unwrap参数并构建数组成功")

    val ret = alloc<jvalue>()

    when (returnTypeShort) {
        'L' -> ret.l = if (isStatic) jni.CallStaticObjectMethodA!!(env, manDeclareClz, methodId, cJArgs)
        else jni.CallNonvirtualObjectMethodA!!(env, instance, manDeclareClz, methodId, cJArgs)

        'Z' -> ret.z = if (isStatic) jni.CallStaticBooleanMethodA!!(env, manDeclareClz, methodId, cJArgs)
        else jni.CallNonvirtualBooleanMethodA!!(env, instance, manDeclareClz, methodId, cJArgs)

        'B' -> ret.b = if (isStatic) jni.CallStaticByteMethodA!!(env, manDeclareClz, methodId, cJArgs)
        else jni.CallNonvirtualByteMethodA!!(env, instance, manDeclareClz, methodId, cJArgs)

        'C' -> ret.c = if (isStatic) jni.CallStaticCharMethodA!!(env, manDeclareClz, methodId, cJArgs)
        else jni.CallNonvirtualCharMethodA!!(env, instance, manDeclareClz, methodId, cJArgs)

        'S' -> ret.s = if (isStatic) jni.CallStaticShortMethodA!!(env, manDeclareClz, methodId, cJArgs)
        else jni.CallNonvirtualShortMethodA!!(env, instance, manDeclareClz, methodId, cJArgs)

        'I' -> ret.i = if (isStatic) jni.CallStaticIntMethodA!!(env, manDeclareClz, methodId, cJArgs)
        else jni.CallNonvirtualIntMethodA!!(env, instance, manDeclareClz, methodId, cJArgs)

        'J' -> ret.j = if (isStatic) jni.CallStaticLongMethodA!!(env, manDeclareClz, methodId, cJArgs)
        else jni.CallNonvirtualLongMethodA!!(env, instance, manDeclareClz, methodId, cJArgs)

        'F' -> ret.f = if (isStatic) jni.CallStaticFloatMethodA!!(env, manDeclareClz, methodId, cJArgs)
        else jni.CallNonvirtualFloatMethodA!!(env, instance, manDeclareClz, methodId, cJArgs)

        'D' -> ret.d = if (isStatic) jni.CallStaticDoubleMethodA!!(env, manDeclareClz, methodId, cJArgs)
        else jni.CallNonvirtualDoubleMethodA!!(env, instance, manDeclareClz, methodId, cJArgs)

        'V' -> {
            if (isStatic) jni.CallStaticVoidMethodA!!(env, manDeclareClz, methodId, cJArgs)
            else jni.CallNonvirtualVoidMethodA!!(env, instance, manDeclareClz, methodId, cJArgs)
            ret.l = null
        }

        else -> error("鹤移位")
    }
    Log.d(TAG, "就是这个！")

    if (env.hasException()) {
        val exception = jni.ExceptionOccurred!!(env)
        jni.ExceptionClear!!(env)
        Log.d(TAG, "有Java侧错误，懒得看了")
        jni.Throw!!(env, exception)

        return null // 既抛则不二抛
    }
    Log.d(TAG, "我挚爱的杰作")

    Log.d(TAG, "行将Wrap并返回")
    return env.wrap(ret, returnTypeShort)
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
    Log.i(TAG, "JNI正常，版本（1.6）不错，我的朋友")

    setupJvmti(vm)
    Log.i(TAG, "JVMTI正常：1.2，完美")
    if (env.hasException()) error("补药啊")

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
    ) error("添加权能不成功")
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
    ) error("添加回调不成功")
    Log.i(TAG, "创建并添加回调成功")
}