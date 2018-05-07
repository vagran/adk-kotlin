package com.ast.adk.utils

import java.io.IOException
import java.io.InputStream
import java.util.*
import kotlin.reflect.KClass

object Resources {

    /** Set application base class. Assets are searched in the directory which is located in the same
     * directory where base class is defined.
     */
    fun SetBaseClass(cls: KClass<*>)
    {
        baseClassLoader = cls.java.classLoader
        packagePath = cls.java.`package`.name.replace(".", "/")
        var props = GetProperties("system-properties.ini")
        if (props == null) {
            /* Ignore missing properties. */
            props = Properties()
        }
        props.putAll(System.getProperties())
        props.setProperty("log4j.configurationFile", "$packagePath/assets/log-config.xml")
        System.setProperties(props)
    }

    /** Get asset resource.
     *
     * @param path Relative to assets directory.
     * @return Asset resource stream. Null if not found.
     */
    fun GetAsset(path: String): InputStream?
    {
        return baseClassLoader!!.getResourceAsStream("$packagePath/assets/$path")
    }

    /** Get properties by path in assets directory. Should be used only for reading.
     * @return Loaded properties, or null if not found.
     */
    fun GetProperties(path: String): Properties?
    {
        var props: Properties? = null
        synchronized(propsCache) {
            props = propsCache[path]
        }
        if (props != null) {
            return props
        }
        val s = GetAsset(path) ?: return null
        props = Properties()
        try {
            props!!.load(s)
        } catch (e: IOException) {
            throw RuntimeException("Failed to load properties: $path", e)
        }

        synchronized(propsCache) {
            propsCache.put(path, props!!)
        }
        return props
    }

    /** Class loader for base class.  */
    private var baseClassLoader: ClassLoader? = null
    /** Package name for base class.  */
    private var packagePath: String? = null
    /** Loaded properties.  */
    private val propsCache = TreeMap<String, Properties>()
}