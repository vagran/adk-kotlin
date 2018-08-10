package com.ast.adk.log

import java.io.ByteArrayOutputStream

/** Output stream to log adapter.  */
class LoggingOutputStream(private val logger: Logger, private val level: LogLevel):
    ByteArrayOutputStream() {

    private val lineSeparator: String = System.getProperty("line.separator")

    override fun flush()
    {
        var record: String

        synchronized(this) {
            super.flush()
            record = toString()
            super.reset()

            if (record.isEmpty() || record == lineSeparator) {
                /* Avoid empty records. */
                return
            }

            logger.Log(level, null, record)
        }
    }
}
