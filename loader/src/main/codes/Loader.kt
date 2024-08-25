package me.earzuchan.sakiko.loader

import me.earzuchan.sakiko.api.SakikoModule
import me.earzuchan.sakiko.api.annotations.ExposedSakikoModule
import me.earzuchan.sakiko.core.Runner
import me.earzuchan.sakiko.api.utils.Log
import java.io.File
import java.lang.instrument.Instrumentation
import java.util.jar.JarFile

const val TAG = "SakikoLoader"

object LoaderEntry {
    @JvmStatic
    fun premain(agentArgs: String?, inst: Instrumentation) {
        Log.info(TAG, "Loader Entry")

        agentArgs?.let { arg ->
            Log.info(TAG, "Agent Args: $arg")

            // 按','分割参数
            val jarPaths = arg.split(',')

            val moduleClasses = mutableListOf<Class<out SakikoModule>>()

            jarPaths.forEach {
                File(it).let { file ->
                    if (file.exists()) {
                        Log.info(TAG, "Checking Jar: ${file.name}")

                        JarFile(file).use { jar ->
                            val entries = jar.entries()
                            while (entries.hasMoreElements()) {
                                val entry = entries.nextElement()
                                if (entry.name.endsWith(".class")) {
                                    val classBytes = jar.getInputStream(entry).readBytes()
                                    val className = entry.name.removeSuffix(".class").replace('/', '.')
                                    val clazz = loadClassFromBytes(classBytes, className)

                                    if (clazz != null && clazz.annotations.any { it.annotationClass == ExposedSakikoModule::class }
                                        && SakikoModule::class.java.isAssignableFrom(clazz)) {
                                        Log.info(TAG, "Found Module Class: $className")

                                        moduleClasses.add(clazz as Class<out SakikoModule>)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            moduleClasses.forEach {
                Runner.loadModule(it)
            }
        }
    }

    // 使用隔离的ClassLoader加载类
    private fun loadClassFromBytes(classBytes: ByteArray, className: String): Class<*>? {
        return try {
            val classLoader = object : ClassLoader() {
                override fun findClass(name: String): Class<*> {
                    return defineClass(name, classBytes, 0, classBytes.size)
                }
            }
            classLoader.loadClass(className)
        } catch (e: Exception) {
            Log.error(TAG, "Failed to load class: $className")
            null
        }
    }
}