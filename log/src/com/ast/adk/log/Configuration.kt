package com.ast.adk.log

import com.google.gson.GsonBuilder
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.regex.Pattern

class Configuration {

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

        private fun FromJsonObject(obj: Any): Configuration
        {

            return Configuration()
        }
    }

    class Appender {

        lateinit var name: String
        lateinit var type: Type
        var pattern: String? = null
        var level: LogLevel? = null
        var consoleParams: ConsoleParams? = null

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
        }

        class FileParams {
            lateinit var path: Path
            var maxSize: Long? = null
            var maxTime: Duration? = null
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
