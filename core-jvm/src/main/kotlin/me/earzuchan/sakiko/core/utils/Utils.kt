package me.earzuchan.sakiko.core.utils

import net.bytebuddy.jar.asm.Opcodes
import me.earzuchan.sakiko.core.SakiNative
import net.bytebuddy.jar.asm.ClassReader
import net.bytebuddy.jar.asm.ClassVisitor
import net.bytebuddy.jar.asm.ClassWriter
import net.bytebuddy.jar.asm.Label
import net.bytebuddy.jar.asm.MethodVisitor
import net.bytebuddy.jar.asm.Type
import java.io.File
import java.lang.invoke.MethodHandles
import java.lang.reflect.Constructor
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

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
    private val cannotBeThatException = IllegalStateException("怎么会是呢")

    fun weave(man: Member, oldBc: ByteArray, hookId: Long): ByteArray {
        val isStatic = (man.modifiers and Modifier.STATIC) != 0
        val methodName = when (man) {
            is Constructor<*> -> if (isStatic) "<clinit>" else "<init>"
            is Method -> man.name
            else -> throw cannotBeThatException
        }
        val methodDesc = when (man) {
            is Constructor<*> -> Type.getConstructorDescriptor(man)
            is Method -> Type.getMethodDescriptor(man)
        }

        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        var hookInfo: HookInfo

        ClassReader(oldBc).accept(object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visitMethod(
                access: Int, name: String, descriptor: String,
                signature: String?, exceptions: Array<String>?
            ): MethodVisitor {
                if (name != methodName || descriptor != methodDesc)
                    return super.visitMethod(access, name, descriptor, signature, exceptions)

                hookInfo = HookInfo(
                    isStatic = (access and Opcodes.ACC_STATIC) != 0,
                    hookId = hookId,
                    paramTypes = Type.getArgumentTypes(descriptor),
                    returnType = Type.getReturnType(descriptor)
                )

                return object :
                    MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    override fun visitCode() {
                        super.visitCode()
                        generateHookPrologue(mv, hookInfo)
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
        val returnType: Type
    ) {
        override fun equals(other: Any?) = this === other
        override fun hashCode() = System.identityHashCode(this)
    }

    private fun generateHookPrologue(mv: MethodVisitor, info: HookInfo) {
        val arrayLen = info.paramTypes.size + (if (info.isStatic) 1 else 2)
        val label = Label()
        mv.visitLabel(label)
        mv.visitLineNumber(1, label)

        // Create args array
        mv.visitIntInsn(Opcodes.BIPUSH, arrayLen)
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object")

        // args[0] = hookId
        mv.visitInsn(Opcodes.DUP)
        mv.visitIntInsn(Opcodes.BIPUSH, 0)
        mv.visitLdcInsn(info.hookId)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false)
        mv.visitInsn(Opcodes.AASTORE)

        var argIdx = 1
        var localIdx = 0

        // Copy 'this' if not static
        if (!info.isStatic) {
            copyArgToArray(mv, 0, argIdx++, 'L')
            localIdx = 1
        }

        // Copy parameters
        for (paramType in info.paramTypes) {
            val shorty = paramType.descriptor[0]
            copyArgToArray(mv, localIdx, argIdx++, shorty)
            localIdx += if (shorty in "JD") 2 else 1
        }

        // Call SakiBridgeImpl.relay
        mv.visitVarInsn(Opcodes.ASTORE, localIdx)
        mv.visitVarInsn(Opcodes.ALOAD, localIdx)
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "me/earzuchan/sakiko/core/SakiBridgeImpl",
            "relay",
            "([Ljava/lang/Object;)[Ljava/lang/Object;",
            false
        )
        mv.visitVarInsn(Opcodes.ASTORE, localIdx + 1)

        // Handle result
        mv.visitVarInsn(Opcodes.ALOAD, localIdx + 1)
        val originLabel = Label()
        mv.visitJumpInsn(Opcodes.IFNULL, originLabel)

        val returnShorty = info.returnType.descriptor[0]
        when {
            returnShorty == 'V' -> mv.visitInsn(Opcodes.RETURN)
            returnShorty in "JFD" || (returnShorty !in "L[") -> {
                mv.visitVarInsn(Opcodes.ALOAD, localIdx + 1)
                mv.visitInsn(Opcodes.ICONST_0)
                mv.visitInsn(Opcodes.AALOAD)
                if (returnShorty !in "L[") unboxPrimitive(mv, returnShorty)
                else mv.visitTypeInsn(Opcodes.CHECKCAST, info.returnType.internalName)
                mv.visitInsn(primitiveReturnOp(returnShorty))
            }

            else -> {
                mv.visitVarInsn(Opcodes.ALOAD, localIdx + 1)
                mv.visitInsn(Opcodes.ICONST_0)
                mv.visitInsn(Opcodes.AALOAD)
                mv.visitTypeInsn(Opcodes.CHECKCAST, info.returnType.internalName)
                mv.visitInsn(Opcodes.ARETURN)
            }
        }

        mv.visitLabel(originLabel)
    }

    private fun copyArgToArray(mv: MethodVisitor, localIdx: Int, arrIdx: Int, shorty: Char) {
        mv.visitInsn(Opcodes.DUP)
        mv.visitIntInsn(Opcodes.BIPUSH, arrIdx)
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
                else -> throw IllegalArgumentException()
            }
            mv.visitVarInsn(primitiveLoadOp(shorty), localIdx)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, wrapper, "valueOf", "($shorty)L$wrapper;", false)
        } else {
            mv.visitVarInsn(Opcodes.ALOAD, localIdx)
        }
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

    private fun unboxPrimitive(mv: MethodVisitor, s: Char) {
        val wrapper = when (s) {
            'Z' -> "java/lang/Boolean"
            'B' -> "java/lang/Byte"
            'C' -> "java/lang/Character"
            'S' -> "java/lang/Short"
            'I' -> "java/lang/Integer"
            'J' -> "java/lang/Long"
            'F' -> "java/lang/Float"
            'D' -> "java/lang/Double"
            else -> return
        }
        val methodName = when (s) {
            'Z' -> "booleanValue"
            'B' -> "byteValue"
            'C' -> "charValue"
            'S' -> "shortValue"
            'I' -> "intValue"
            'J' -> "longValue"
            'F' -> "floatValue"
            'D' -> "doubleValue"
            else -> return
        }
        mv.visitTypeInsn(Opcodes.CHECKCAST, wrapper)
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, wrapper, methodName, "()$s", false)
    }
}

object ByteCodeVerifier {
    fun verify(bc: ByteArray): Unit = TODO()
}

object InvokeHelper {
    fun Member.invokeUnwraply(thiz: Any?, vararg args: Any?): Any? {
        // 设置访问权限
        when (this) {
            is Method -> this.isAccessible = true
            is Constructor<*> -> this.isAccessible = true
        }

        val lookup = MethodHandles.lookup()

        return when (this) {
            is Method -> {
                if (this.name == "<clinit>") TODO("暂不能") // CHECK：原版能，得实现
                val mh = lookup.unreflect(this)
                if (Modifier.isStatic(this.modifiers)) mh.invokeWithArguments(*args)
                else mh.invokeWithArguments(thiz, *args)
            }

            is Constructor<*> -> {
                val mh = lookup.unreflectConstructor(this)
                mh.invokeWithArguments(*args)
            }

            else -> throw IllegalArgumentException("何意味：$this")
        }
    }

}