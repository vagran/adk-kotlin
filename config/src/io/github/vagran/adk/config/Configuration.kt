/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.config

import io.github.vagran.adk.json.Json
import io.github.vagran.adk.json.adk_codecs.adkJsonCodecRegistry
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.stream.Stream
import java.util.stream.StreamSupport

/** Represents configuration data.  */
class Configuration {

    // /////////////////////////////////////////////////////////////////////////////////////////////////
    private var root: Any? = null
    private val json = Json(true, additionalRegistries = listOf(adkJsonCodecRegistry))

    constructor() {}

    /** Load configuration from the specified file. Currently JSON format is used.  */
    fun Load(path: Path)
    {
        Files.newInputStream(path).use { s -> Load(s) }
    }

    fun Load(s: InputStream)
    {
        InputStreamReader(s).use { reader ->
            root = json.FromJson<Any>(reader)
        }
    }

    fun Save(path: Path)
    {
        Files.newBufferedWriter(path).use { writer ->
            json.ToJson(root, writer)
        }
    }

    /** Merge other configuration into this one. Actually only maps are merged. Values of any other
     * type are overwritten.
     * @param other Configuration to merge into this one.
     */
    fun Merge(other: Configuration)
    {
        root = MergeConfigEntry(root, other.root)
    }

    fun GetString(path: String, allowMissing: Boolean): String?
    {
        return (ResolvePath(path, allowMissing) ?: return null) as? String
            ?: throw Error("Expected string in configuration entry: $path")
    }

    fun GetString(path: String): String
    {
        return GetString(path, false) as String
    }

    fun GetPath(path: String, allowMissing: Boolean): Path?
    {
        val s = GetString(path, allowMissing) ?: return null
        return Paths.get(s)
    }

    fun GetPath(path: String): Path
    {
        return GetPath(path, false) as Path
    }

    fun GetInteger(path: String, allowMissing: Boolean): Int?
    {
        val obj = ResolvePath(path, allowMissing) ?: return null
        if (obj is Double) {
            return obj.toInt()
        }
        if (obj !is Int) {
            throw Error("Expected integer in configuration entry: $path")
        }
        return obj
    }

    fun GetInteger(path: String): Int
    {
        return GetInteger(path, false) as Int
    }

    fun GetLong(path: String, allowMissing: Boolean): Long?
    {
        val obj = ResolvePath(path, allowMissing) ?: return null
        if (obj is Double) {
            return obj.toLong()
        }
        if (obj !is Long) {
            throw Error("Expected long integer in configuration entry: $path")
        }
        return obj
    }

    fun GetLong(path: String): Long
    {
        return GetLong(path, false) as Long
    }

    fun GetSubConfig(path: String, allowMissing: Boolean): Configuration?
    {
        val obj = ResolvePath(path, allowMissing) ?: return null
        return Configuration(obj)
    }

    fun GetSubConfig(path: String): Configuration
    {
        return GetSubConfig(path, false) as Configuration
    }

    /** Entry in an iterable configuration value.  */
    class Entry internal constructor(key: Any, value: Any)
    {
        /** Either key of a map or index of a list.  */
        val key: String = key.toString()
        val value: Configuration = Configuration(
            value)
    }


    /** Iterate values of the provided target. The target pointed by the path should be either list
     * or map.
     *
     * @return True if all value iterated, false if interrupted by the consumer.
     */
    @Suppress("UNCHECKED_CAST")
    fun GetIterator(path: String, allowMissing: Boolean): Iterator<Entry>?
    {
        val obj = ResolvePath(path, allowMissing) ?: return null

        when (obj) {
            is Map<*, *> -> {
                val it = (obj as Map<String, Any>).entries.iterator()
                return object: Iterator<Entry> {

                    override fun hasNext(): Boolean
                    {
                        return it.hasNext()
                    }

                    override fun next(): Entry
                    {
                        val e = it.next()
                        return Entry(
                            e.key,
                            e.value)
                    }
                }

            }

            is List<*> -> {
                val it = (obj as List<String>).listIterator()
                return object: Iterator<Entry> {

                    override fun hasNext(): Boolean
                    {
                        return it.hasNext()
                    }

                    override fun next(): Entry
                    {
                        val idx = it.nextIndex()
                        return Entry(
                            idx.toString(), it.next())
                    }
                }
            }

            else -> throw Error("Non-iterable configuration entry: $path")
        }
    }

    fun GetIterator(path: String): Iterator<Entry>?
    {
        return GetIterator(path, false)
    }

    /** Iterate values of the provided target. The target pointed by path should be either list or
     * map.
     * @param consumer Should return true to continue iteration, false to interrupt.
     * @return True if all value iterated, false if interrupted by the consumer.
     */
    fun ForEach(path: String, consumer: (entry: Entry) -> Boolean, allowMissing: Boolean): Boolean
    {
        val it = GetIterator(path, allowMissing) ?: return true
        while (it.hasNext()) {
            if (!consumer(it.next())) {
                return false
            }
        }
        return true
    }

    fun ForEach(path: String, consumer: (entry: Entry) -> Boolean): Boolean
    {
        return ForEach(path, consumer, false)
    }

    fun GetStream(path: String, allowMissing: Boolean = false): Stream<Entry>
    {
        return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(
                GetIterator(path, allowMissing) ?: throw Error("Entry not found: $path"),
                Spliterator.ORDERED),
            false)
    }

    override fun toString(): String
    {
        return json.ToJson(root!!)
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

    private constructor(root: Any)
    {
        this.root = root
    }

    private fun SplitPath(path: String?): List<String>
    {
        val result = ArrayList<String>()
        if (path == null) {
            return result
        }
        val len = path.length
        var lastPos = 0
        for (i in 0 until len) {
            if (path[i] == '/') {
                result.add(path.substring(lastPos, i))
                lastPos = i + 1
            }
        }
        if (lastPos != len) {
            result.add(path.substring(lastPos, len))
        }
        return result
    }

    /**
     * Resolve configuration
     * @param path
     * @param allowMissing
     * @return Null if not found.
     */
    private fun ResolvePath(path: String, allowMissing: Boolean): Any?
    {
        return ResolvePath(SplitPath(path), allowMissing)
    }

    @Suppress("UNCHECKED_CAST")
    private fun ResolvePath(path: List<String>, allowMissing: Boolean): Any?
    {
        var curObj = root
        for (comp in path) {
            if (curObj !is Map<*, *> || !(curObj as Map<String, Any>).containsKey(comp)) {

                if (allowMissing) {
                    return null
                }
                throw Error("Configuration element '$comp' not found in path '${path.joinToString("/")}'")
            }
            curObj = curObj[comp]
        }
        return curObj
    }

    @Suppress("UNCHECKED_CAST")
    private fun MergeConfigEntry(dst: Any?, src: Any?): Any?
    {
        if (dst is Map<*, *> && src is Map<*, *>) {
            (src as Map<String, Any>).forEach {
                (key, value) ->
                (dst as MutableMap<String, Any>).merge(key, value) {
                    dst, src ->
                    MergeConfigEntry(dst, src)
                }
            }
            return dst
        }
        return src
    }

}
