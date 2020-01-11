package io.github.vagran.adk.log

import java.time.*

class LogMessage {
    lateinit var timestamp: Instant
    lateinit var level: LogLevel
    lateinit var msg: String
    var exception: Throwable? = null
    lateinit var loggerName: String
    lateinit var appenders: List<Appender>
    /* Fields below initialized depending on requested environment mask. */
    var threadName: String? = null

    val localTime get() = LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault())

    fun SetTimestampNow()
    {
        timestamp = OffsetDateTime.now(ZoneOffset.UTC).toInstant()
    }
}
