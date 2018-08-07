package com.ast.adk.log

import com.google.gson.GsonBuilder
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.ArrayList

class Configuration(val appenders: List<Configuration.Appender>,
                    val loggers: List<Configuration.Logger>) {

    companion object {
        fun FromJson(json: String): Configuration
        {
            val gson = GsonBuilder().create()
            return FromJsonObject(gson.fromJson(json, Object::class.java))
        }

        fun FromJson(json: InputStream): Configuration
        {
            val gson = GsonBuilder().create()
            return FromJsonObject(gson.fromJson(InputStreamReader(json), Object::class.java))
        }

        fun FromJson(jsonFile: Path): Configuration
        {
            Files.newInputStream(jsonFile).use {
                return FromJson(it)
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun FromJsonObject(obj: Any): Configuration
        {
            if (obj !is Map<*, *>) {
                throw Exception("Invalid configuration")
            }
            val appenders = TreeMap<String, Appender>()
            val loggers = TreeMap<String, Logger>()
            if ("appenders" in obj) {
                val appendersObj = obj["appenders"] as? Map<String, Map<String, Any?>>
                    ?: throw Exception("Invalid appenders configuration")
                for ((name, config) in appendersObj) {
                    if (name in appenders) {
                        throw Exception("Duplicated appender name: $name")
                    }
                    appenders[name] = Appender(name).also { it.FromJsonObj(config) }
                }
            }
            if ("loggers" in obj) {
                val loggersObj = obj["loggers"] as? Map<String, Map<String, Any?>>
                    ?: throw Exception("Invalid loggers configuration")
                for ((name, config) in loggersObj) {
                    if (name in loggers) {
                        throw Exception("Duplicated logger name: $name")
                    }
                    loggers[name] = Logger(name).also { it.FromJsonObj(config, appenders) }
                }
            }
            return Configuration(ArrayList(appenders.values), ArrayList(loggers.values))
        }
    }

    class Appender(val name: String) {
        lateinit var type: Type
        var pattern: String? = null
        var level: LogLevel? = null
        var consoleParams: ConsoleParams? = null
        var fileParams: FileParams? = null

        enum class Type {
            CONSOLE,
            FILE;

            companion object {
                fun ByName(name: String): Type
                {
                    try {
                        return Type.valueOf(name.toUpperCase())
                    } catch (e: IllegalArgumentException) {
                        throw Error("Unrecognized appender type: $name")
                    }
                }
            }
        }

        class ConsoleParams {
            enum class Target {
                STDOUT,
                STDERR;

                companion object {
                    fun ByName(name: String): Target
                    {
                        try {
                            return Target.valueOf(name.toUpperCase())
                        } catch (e: IllegalArgumentException) {
                            throw Error("Unrecognized console appender target: $name")
                        }
                    }
                }
            }

            lateinit var target: Target

            fun FromJsonObj(name: String, obj: Map<String, Any?>)
            {
                if ("target" !in obj) {
                    throw Exception("Target should be specified for console appender $name")
                }
                target = Target.ByName(obj["target"] as String)
            }
        }

        class FileParams {
            lateinit var path: Path
            var maxSize: Long? = null
            var maxTime: Duration? = null

            fun FromJsonObj(name: String, obj: Map<String, Any?>)
            {
                if ("path" !in obj) {
                    throw Exception("Path should be specified for file appender $name")
                }
                path = Paths.get(obj["path"] as String)
                maxSize = if ("maxSize" in obj) {
                    ParseSize(obj["maxSize"] as String)
                } else {
                    null
                }
                maxTime = if ("maxTime" in obj) {
                    ParseDuration(obj["maxTime"] as String)
                } else {
                    null
                }
            }
        }

        fun FromJsonObj(obj: Map<String, Any?>)
        {
            if ("type" !in obj) {
                throw Exception("Type not specified for appender $name")
            }
            type = Type.ByName(obj["type"] as String)
            level = if ("level" in obj) {
                LogLevel.ByName(obj["level"] as String)
            } else {
                null
            }
            pattern = if ("pattern" in obj) {
                obj["pattern"] as String
            } else {
                null
            }
            when (type) {
                Type.CONSOLE -> consoleParams = ConsoleParams().also { it.FromJsonObj(name, obj) }
                Type.FILE -> fileParams = FileParams().also { it.FromJsonObj(name, obj) }
            }
        }
    }

    class Logger(val name: String) {
        var level: LogLevel? = null
        lateinit var appenders: List<Appender>

        @Suppress("UNCHECKED_CAST")
        fun FromJsonObj(obj: Map<String, Any?>, knownAppenders: Map<String, Appender>)
        {
            level = if ("level" in obj) {
                LogLevel.ByName(obj["level"] as String)
            } else {
                null
            }
            val _appenders = ArrayList<Appender>()
            if ("appenders" in obj) {
                val list = obj["appenders"] as? List<String>
                    ?: throw Exception("Invalid appenders list for $name")
                for (appName in list) {
                    if (appName !in knownAppenders) {
                        throw Exception("Unregistered appender $appName referenced from logger $name")
                    }
                    _appenders.add(knownAppenders[appName]!!)
                }
            }
            appenders = _appenders
        }
    }
}

private val durationPat = Pattern.compile(
    """(?:(\d+)[dD])?\s*(?:(\d+)[hH])?\s*(?:(\d+)[mM])?\s*(?:(\d+)[sS])?\s*""")

fun ParseDuration(s: String): Duration
{
    val matcher = durationPat.matcher(s)
    var result = Duration.ZERO
    if (matcher.matches()) {
        val dayStart = matcher.start(1)
        if (dayStart >= 0) {
            result = result.plusDays(java.lang.Long.parseLong(s, dayStart, matcher.end(1), 10))
        }
        val hourStart = matcher.start(2)
        if (hourStart >= 0) {
            result = result.plusHours(java.lang.Long.parseLong(s, hourStart, matcher.end(2), 10))
        }
        val minStart = matcher.start(3)
        if (minStart >= 0) {
            result = result.plusMinutes(java.lang.Long.parseLong(s, minStart, matcher.end(3), 10))
        }
        val secStart = matcher.start(4)
        if (secStart >= 0) {
            result = result.plusSeconds(java.lang.Long.parseLong(s, secStart, matcher.end(4), 10))
        }
    } else {
        throw IllegalArgumentException("Invalid duration specified: $s")
    }
    return result
}

fun ParseSize(s: String): Long
{
    if (s.isEmpty()) {
        throw IllegalArgumentException("Empty string for size")
    }

    val factor = when (s[s.length - 1]) {
        'k', 'K' -> 1024L
        'm', 'M' -> 1024L * 1024L
        'g', 'G' -> 1024L * 1024L * 1024L
        else -> 1L
    }

    val base = if (factor == 1L) {
        java.lang.Long.parseLong(s)
    } else {
        java.lang.Long.parseLong(s, 0, s.length - 1, 10)
    }
    return factor * base
}
