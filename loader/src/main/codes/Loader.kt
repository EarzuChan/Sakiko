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
        Log.info(TAG, "Loader Enter")

        agentArgs?.let { arg ->
            Log.info(TAG, "Given Args: $arg")

            val jarPaths = arg.split(',')
            val moduleClasses = jarPaths.mapNotNull { processJarFile(it) }.flatten()

            moduleClasses.forEach {
                Log.info(TAG, "Loading Module: ${it.name}")
                Runner.loadModule(it)
            }
        }
    }

    private fun processJarFile(jarPath: String): List<Class<out SakikoModule>>? {
        val file = File(jarPath)
        if (!file.exists()) {
            Log.warn(TAG, "Jar file does not exist: $jarPath")
            return null
        }

        Log.info(TAG, "Checking Jar: ${file.name}")
        return JarFile(file).use { jar ->
            jar.entries().asSequence().filter { it.name.endsWith(".class") }.mapNotNull { entry ->
                val classBytes = jar.getInputStream(entry).readBytes()
                val className = entry.name.removeSuffix(".class").replace('/', '.')
                loadClassFromBytes(classBytes, className)
            }.filter { clazz ->
                clazz.annotations.any { it.annotationClass == ExposedSakikoModule::class }
                        && SakikoModule::class.java.isAssignableFrom(clazz)
            }.map {
                Log.info(TAG, "Module Class Found: ${it.name}")
                it as Class<out SakikoModule>
            }.toList()
        }
    }

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