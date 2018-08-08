package com.ast.adk.log

import java.time.*

class LogMessage {
    lateinit var timestamp: Instant
    lateinit var level: LogLevel
    lateinit var msg: String
    var exception: Throwable? = null
    lateinit var loggerName: String
    var threadName: String? = null
    lateinit var appenders: List<Appender>

    fun SetTimestampNow()
    {
        timestamp = OffsetDateTime.now(ZoneOffset.UTC).toInstant()
    }

    fun LocalTime(): LocalDateTime
    {
        return LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault())
    }
}
