package com.ast.adk.log

enum class LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARNING,
    ERROR;

    companion object {
        fun ByName(name: String): LogLevel
        {
            try {
                return LogLevel.valueOf(name.toUpperCase())
            } catch (e: IllegalArgumentException) {
                throw Error("Unrecognized log level: $name")
            }
        }
    }
}
