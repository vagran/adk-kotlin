package io.github.vagran.adk.log

enum class LogLevel(val displayName: String) {
    TRACE("TRACE"),
    DEBUG("DEBUG"),
    INFO("INFO"),
    WARNING("WARN"),
    ERROR("ERROR");

    companion object {
        val MIN = TRACE
        val MAX = ERROR

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
