package com.ast.adk.log

import com.ast.adk.json.Json
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.ArrayList

class LogConfiguration(val settings: Settings,
                       val appenders: List<Appender>,
                       val loggers: Map<LoggerName, Logger>) {

    constructor(settings: Settings,
                appenders: List<Appender>,
                loggers: List<Logger>): this(settings, appenders, LoggersListToMap(loggers))

    companion object {
        const val DEFAULT_PATTERN = "%{time:HH:mm:ss.SSS} [%thread] %{level:-5} %logger - %msg"

        fun Default(consoleTarget: Appender.ConsoleParams.Target =
                        Appender.ConsoleParams.Target.STDERR,
                    thresholdLevel: LogLevel = LogLevel.TRACE): LogConfiguration
        {
            val appender = Appender("console").apply {
                type = Appender.Type.CONSOLE
                pattern = DEFAULT_PATTERN
                level = thresholdLevel
                consoleParams = Appender.ConsoleParams().apply {
                    target = consoleTarget
                }
            }
            val appenders = listOf(appender)

            val logger = Logger(LoggerName.ROOT).apply {
                this.level = thresholdLevel
                this.appenders = appenders
            }

            return LogConfiguration(Settings(), appenders, mapOf(logger.name to logger))
        }

        fun FromJson(json: String): LogConfiguration
        {
            return FromJsonObject(Json().FromJson(json)!!)
        }

        fun FromJson(json: InputStream): LogConfiguration
        {
            return FromJsonObject(Json().FromJson(json)!!)
        }

        fun FromJson(jsonFile: Path): LogConfiguration
        {
            Files.newInputStream(jsonFile).use {
                return FromJson(it)
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun FromJsonObject(obj: Any): LogConfiguration
        {
            if (obj !is Map<*, *>) {
                throw Exception("Invalid configuration")
            }

            val settings = Settings()
            if ("settings" in obj) {
                val settingsObj = obj["settings"] as? Map<String, Any?>
                    ?: throw Exception("Invalid settings configuration")
                settings.FromJsonObj(settingsObj)
            }

            val appenders = TreeMap<String, Appender>()
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

            val loggers = TreeMap<LoggerName, Logger>()
            if ("loggers" in obj) {
                val loggersObj = obj["loggers"] as? Map<String, Map<String, Any?>>
                    ?: throw Exception("Invalid loggers configuration")
                for ((name, config) in loggersObj) {
                    val loggerName = if (name == "root") {
                        LoggerName.ROOT
                    } else {
                        LoggerName(name)
                    }
                    if (loggerName in loggers) {
                        throw Exception("Duplicated logger name: $name")
                    }
                    loggers[loggerName] = Logger(loggerName).also { it.FromJsonObj(config, appenders) }
                }
            }

            return LogConfiguration(settings, ArrayList(appenders.values), loggers)
        }

        private fun LoggersListToMap(loggers: List<Logger>): Map<LoggerName, Logger>
        {
            val result = TreeMap<LoggerName, Logger>()
            for (logger in loggers) {
                result[logger.name] = logger
            }
            return result
        }
    }

    class Settings {
        var queueSize = 10000
        var queueCheckInterval = 100L
        var overflowBlocks = true

        fun FromJsonObj(obj: Map<String, Any?>)
        {
            if ("queueSize" in obj) {
                queueSize = (obj["queueSize"] as Double).toInt()
            }
            if ("queueCheckInterval" in obj) {
                queueCheckInterval = (obj["queueCheckInterval"] as Double).toLong()
            }
            if ("overflowBlocks" in obj) {
                overflowBlocks = obj["overflowBlocks"] as Boolean
            }
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
            var compressOld = false
            var preserveNum: Int? = null

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
                compressOld = if ("compressOld" in obj) {
                    obj["compressOld"] as Boolean
                } else {
                    false
                }
                preserveNum = if ("preserveNum" in obj) {
                    (obj["preserveNum"] as Double).toInt()
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

    class Logger(val name: LoggerName) {
        var level: LogLevel? = null
        lateinit var appenders: List<Appender>
        var additiveAppenders = true

        constructor(name: String): this(LoggerName(name))

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
            if ("additiveAppenders" in obj) {
                additiveAppenders = obj["additiveAppenders"] as Boolean
            } else {
                additiveAppenders = true
            }
            appenders = _appenders
        }
    }

    class LoggerParams {
        lateinit var level: LogLevel
        lateinit var appenders: List<Appender>
    }

    /** Check the specified logger name against the configuration and infer its parameters. */
    fun ConfigureLogger(name: String): LoggerParams
    {
        val loggerName = LoggerName(name)
        val appenders = ArrayList<Appender>()
        var level = LogLevel.MIN

        for (i in 0..loggerName.length) {
            val logger = loggers[loggerName.Prefix(i)] ?: continue
            if (logger.level != null) {
                level = logger.level!!
            }
            if (!logger.additiveAppenders) {
                appenders.clear()
            }
            appenders.addAll(logger.appenders)
        }

        return LoggerParams().apply {
            this.level = level
            this.appenders = appenders
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
