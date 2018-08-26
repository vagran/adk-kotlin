package com.ast.adk.log.slf4j.api

import com.ast.adk.log.LogConfiguration
import com.ast.adk.log.LogManager

/** This object provides control over log manager instance used by SLF4J bindings. */
object Slf4jLogManager {

    var logManager: LogManager
        get() {
            var _logManager = this.logManagerImpl
            if (_logManager == null) {
                _logManager = synchronized(this) {
                    if (this.logManagerImpl == null) {
                        this.logManagerImpl = LogManager().apply {
                            Initialize(LogConfiguration.Default())
                        }
                    }
                    this.logManagerImpl!!
                }
            }
            return _logManager
        }

        set(value) {
            val old = synchronized(this) {
                val old = logManagerImpl
                logManagerImpl = value
                old
            }
            old?.Shutdown()
        }

    fun Shutdown()
    {
        logManagerImpl?.Shutdown()
    }

    @Volatile private var logManagerImpl: LogManager? = null
}
