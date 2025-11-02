package me.earzuchan.sakiko.core.utils

import me.earzuchan.sakiko.api.utils.SLog
import me.earzuchan.sakiko.core.SakiNative
import org.objectweb.asm.*
import org.objectweb.asm.util.CheckClassAdapter
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.Byte
import java.lang.Double
import java.lang.Float
import java.lang.Short
import java.lang.ref.Reference
import java.lang.reflect.Constructor
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.ByteArray
import kotlin.Char
import kotlin.IllegalArgumentException
import kotlin.IllegalStateException
import kotlin.Int
import kotlin.Long
import kotlin.RuntimeException
import kotlin.String
import kotlin.Suppress
import kotlin.UnsupportedOperationException
import kotlin.require

object NativeUtils {
    private val osName = System.getProperty("os.name").lowercase()
    private val osArch = System.getProperty("os.arch").lowercase()

    @Suppress("UnsafeDynamicallyLoadedCode")
    fun loadLibrary(libName: String) = try {
        System.loadLibrary(libName)
    } catch (_: UnsatisfiedLinkError) {
        val nativeLib = extractNativeLib(libName)
        System.load(nativeLib.absolutePath)
    }

    private fun extractNativeLib(libName: String): File {
        val platform = getPlatform()
        val libFileName = getLibFileName(libName)
        val resourcePath = "/native/$platform/$libFileName"

        val tempDir = Files.createTempDirectory("native_libs").toFile()
        val libFile = File(tempDir, libFileName)

        javaClass.getResourceAsStream(resourcePath)?.use { input ->
            Files.copy(input, libFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } ?: throw RuntimeException("在应许之地找不到：$resourcePath")

        libFile.deleteOnExit()
        return libFile
    }

    private fun getPlatform(): String = when {
        osName.contains("windows") && osArch.contains("amd64") -> "win-x64"
        osName.contains("linux") && osArch.contains("x86_64") -> "linux-x64"
        osName.contains("mac") && osArch.contains("aarch64") -> "mac-arm64"
        else -> throw UnsupportedOperationException("Unsupported OS: $osName $osArch")
    }

    private fun getLibFileName(libName: String): String = when {
        osName.contains("win") -> "$libName.dll"
        osName.contains("mac") -> "lib$libName.dylib"
        else -> "lib$libName.so"
    }
}

object ByteCodeStorage {
    val classMap = ConcurrentHashMap<Class<*>, ByteArray>()

    fun getByClass(targetClass: Class<*>): ByteArray =
        classMap[targetClass] ?: SakiNative.getClassByteCode(targetClass)

    // 把改后的暂存
    fun setByClass(targetClass: Class<*>, bytes: ByteArray) {
        classMap[targetClass] = bytes
    }
}

object ByteCodeWeaver {
    private const val TAG = "ByteCodeWeaver"

    private val cannotBeThatException = IllegalStateException("怎么会是呢")

    fun ByteArray.weave(man: Member, hookId: Long, relayBlazzName: String): ByteArray {
        val isStatic = Modifier.isStatic(man.modifiers)

        val (methodName, methodDesc) = when (man) {
            is Constructor<*> -> (if (isStatic) "<clinit>" else "<init>") to Type.getConstructorDescriptor(man)
            is Method -> man.name to Type.getMethodDescriptor(man)
            else -> throw cannotBeThatException
        }

        // SLog.debug("看看desc而已：$methodDesc", TAG)

        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)

        ClassReader(this).accept(object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visitMethod(
                access: Int, name: String, descriptor: String,
                signature: String?, exceptions: Array<String>?
            ): MethodVisitor {
                // 是否匹配
                if (name != methodName || descriptor != methodDesc)
                    return super.visitMethod(access, name, descriptor, signature, exceptions)

                return object :
                    MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {

                    override fun visitCode() {
                        super.visitCode()

                        // val isStaticBC = (access and Opcodes.ACC_STATIC) != 0
                        // SLog.debug("看看静态：$isStatic，$isStaticBC", TAG)

                        generateHookPrologue(
                            mv, HookInfo(
                                isStatic, hookId, Type.getArgumentTypes(descriptor),
                                Type.getReturnType(descriptor), relayBlazzName
                            )
                        )
                    }
                }
            }
        }, 0)

        return cw.toByteArray()
    }

    private data class HookInfo(
        val isStatic: Boolean,
        val hookId: Long,
        val paramTypes: Array<Type>,
        val returnType: Type,
        val relayBlazzName: String,
    ) {
        override fun equals(other: Any?) = this === other
        override fun hashCode() = System.identityHashCode(this)
    }

    private fun generateHookPrologue(visitor: MethodVisitor, info: HookInfo) {
        val arrayLen = info.paramTypes.size + (if (info.isStatic) 1 else 2)

        val prologueLabel = Label()
        visitor.visitLabel(prologueLabel)
        visitor.visitLineNumber(1, prologueLabel)

        // 创建Args数组
        visitor.visitIntInsn(Opcodes.BIPUSH, arrayLen)
        visitor.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object")

        // args[0] = hookId
        visitor.visitInsn(Opcodes.DUP)
        visitor.visitIntInsn(Opcodes.BIPUSH, 0)
        visitor.visitLdcInsn(info.hookId)
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false)
        visitor.visitInsn(Opcodes.AASTORE)

        var argIndex = 1
        var localIndex = 0

        // 如果非静态，拷贝this（0位）
        if (!info.isStatic) {
            copyArgToArray(visitor, 0, argIndex++, 'L')
            localIndex = 1
        }

        // 拷贝参数
        for (paramType in info.paramTypes) {
            val shorty = paramType.descriptor[0]
            copyArgToArray(visitor, localIndex, argIndex++, shorty)

            // LONG和DOUBLE占的`位宽`不一样
            localIndex += if (shorty in "JD") 2 else 1
        }

        // 调用接力
        visitor.visitVarInsn(Opcodes.ASTORE, localIndex)
        visitor.visitVarInsn(Opcodes.ALOAD, localIndex)
        visitor.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            info.relayBlazzName,
            "relay",
            "([Ljava/lang/Object;)[Ljava/lang/Object;",
            false
        )
        visitor.visitVarInsn(Opcodes.ASTORE, localIndex + 1)

        // 处理结果
        visitor.visitVarInsn(Opcodes.ALOAD, localIndex + 1)
        val originLabel = Label()
        visitor.visitJumpInsn(Opcodes.IFNULL, originLabel)

        when (val returnShorty = info.returnType.descriptor[0]) {
            'V' -> visitor.visitInsn(Opcodes.RETURN)

            else -> {
                visitor.visitVarInsn(Opcodes.ALOAD, localIndex + 1)
                visitor.visitInsn(Opcodes.ICONST_0)
                visitor.visitInsn(Opcodes.AALOAD)

                // 基本类型与否
                if (returnShorty !in "L[") {
                    unwrapPrimitive(visitor, returnShorty)
                    visitor.visitInsn(primitiveReturnOp(returnShorty))
                } else {
                    visitor.visitTypeInsn(Opcodes.CHECKCAST, info.returnType.internalName)
                    visitor.visitInsn(Opcodes.ARETURN)
                }
            }
        }

        visitor.visitLabel(originLabel)
    }

    private fun copyArgToArray(mv: MethodVisitor, localIndex: Int, arrayIndex: Int, shorty: Char) {
        mv.visitInsn(Opcodes.DUP)
        mv.visitIntInsn(Opcodes.BIPUSH, arrayIndex)

        // 是基本类型，就包装；否则直接加载
        if (shorty !in "L[") {
            val wrapper = when (shorty) {
                'Z' -> "java/lang/Boolean"
                'B' -> "java/lang/Byte"
                'C' -> "java/lang/Character"
                'S' -> "java/lang/Short"
                'I' -> "java/lang/Integer"
                'J' -> "java/lang/Long"
                'F' -> "java/lang/Float"
                'D' -> "java/lang/Double"
                else -> throw cannotBeThatException
            }
            mv.visitVarInsn(primitiveLoadOp(shorty), localIndex)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, wrapper, "valueOf", "($shorty)L$wrapper;", false)
        } else mv.visitVarInsn(Opcodes.ALOAD, localIndex)

        mv.visitInsn(Opcodes.AASTORE)
    }

    private fun primitiveLoadOp(s: Char) = when (s) {
        'J' -> Opcodes.LLOAD
        'F' -> Opcodes.FLOAD
        'D' -> Opcodes.DLOAD
        else -> Opcodes.ILOAD
    }

    private fun primitiveReturnOp(s: Char) = when (s) {
        'J' -> Opcodes.LRETURN
        'F' -> Opcodes.FRETURN
        'D' -> Opcodes.DRETURN
        'V' -> Opcodes.RETURN
        else -> Opcodes.IRETURN
    }

    private fun unwrapPrimitive(mv: MethodVisitor, s: Char) {
        val (wrapper, methodName) = when (s) {
            'Z' -> "java/lang/Boolean" to "booleanValue"
            'B' -> "java/lang/Byte" to "byteValue"
            'C' -> "java/lang/Character" to "charValue"
            'S' -> "java/lang/Short" to "shortValue"
            'I' -> "java/lang/Integer" to "intValue"
            'J' -> "java/lang/Long" to "longValue"
            'F' -> "java/lang/Float" to "floatValue"
            'D' -> "java/lang/Double" to "doubleValue"
            else -> return
        }
        mv.visitTypeInsn(Opcodes.CHECKCAST, wrapper)
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, wrapper, methodName, "()$s", false)
    }
}

object ByteCodeVerifier {
    fun verify(bc: ByteArray) {
        val verificationResult = StringWriter().use { stringWriter ->
            PrintWriter(stringWriter).use { printWriter ->
                CheckClassAdapter.verify(ClassReader(bc), false, printWriter)
            }
            stringWriter.toString()
        }

        require(verificationResult.isEmpty()) {
            "字节码校验失败：$verificationResult"
        }
    }
}

object MambaUtils {
    fun Member.proInvoke(instance: Any?, vararg args: Any?): Any? {
        val TAG = "MemberProInvoke"

        // CHECK：是否检测INSTANCE是否符合，参数列表长度等

        return when (this) {
            is Method -> {
                val isStatic = Modifier.isStatic(modifiers)
                val specSign = getSpecSign()
                SLog.debug("ProInv方法，静态：$isStatic，特签：$specSign", TAG)
                SakiNative.proInvoke(this, specSign, declaringClass, isStatic, instance, args)
            }

            is Constructor<*> -> {
                // FIXME：得走Native
                val specSign = getSpecSign()
                SLog.debug("ProInv构造器，特签：$specSign", TAG)
                SakiNative.proInvoke(this, specSign, declaringClass, false, instance, args)
            }

            else -> throw IllegalArgumentException("何意味：$this")
        }
    }

    // 获取类型特签
    fun Class<*>.getSpecSign(): Char = when {
        isPrimitive -> when (this) {
            Int::class.javaPrimitiveType -> 'I'
            Void::class.javaPrimitiveType -> 'V'
            Boolean::class.javaPrimitiveType -> 'Z'
            Char::class.javaPrimitiveType -> 'C'
            Byte::class.javaPrimitiveType -> 'B'
            Short::class.javaPrimitiveType -> 'S'
            Float::class.javaPrimitiveType -> 'F'
            Long::class.javaPrimitiveType -> 'J'
            Double::class.javaPrimitiveType -> 'D'
            else -> throw IllegalArgumentException("你妈：$name")
        }

        // 数组和非基本类型
        else -> 'L'
    }

    // 获取方法特签
    fun Method.getSpecSign(): String = StringBuilder().apply {
        parameterTypes.forEach { append(it.getSpecSign()) }
        append(',')
        append(returnType.getSpecSign())
    }.toString()

    // 获取构造器特签
    fun Constructor<*>.getSpecSign(): String = StringBuilder().apply {
        parameterTypes.forEach { append(it.getSpecSign()) }
        append(",V")
    }.toString()

    fun getClInitOrNull(targetClass: Class<*>): Constructor<*>? {
        // 强制准备类
        Reference.reachabilityFence(targetClass.declaredMethods)
        return SakiNative.getClInitOrNull(targetClass)
    }
}