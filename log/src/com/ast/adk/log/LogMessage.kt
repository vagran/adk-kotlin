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

    val localTime get() = LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault())

    fun SetTimestampNow()
    {
        timestamp = OffsetDateTime.now(ZoneOffset.UTC).toInstant()
    }
}
