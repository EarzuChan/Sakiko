@file:OptIn(ExperimentalForeignApi::class)

package me.earzuchan.sakiko.native.models

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.invoke
import kotlinx.cinterop.pointed
import libjava.*

class JObjectStorage(
    private val jniEnv: CPointer<JNIEnvVar>,
    obj: jobject
) : AutoCloseable {
    private val jniEnvInterface = jniEnv.pointed.pointed!!

    private var globalRef: jobject? = jniEnvInterface.NewGlobalRef!!(jniEnv, obj)
        ?: throw IllegalStateException("创建全局引用失败")

    fun peek(): jobject? = globalRef

    fun isValid(): Boolean = globalRef != null && jniEnv != null

    fun release(): jobject? { // 转交所有权
        val tmp = globalRef
        globalRef = null  // 防止析构时删除
        return tmp
    }

    override fun close() {
        if (globalRef != null && jniEnv != null) {
            jniEnvInterface.DeleteGlobalRef!!(jniEnv, globalRef) // CHECK
            globalRef = null
        }
    }
}