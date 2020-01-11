/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.log.slf4j.api

import io.github.vagran.adk.log.LogConfiguration
import io.github.vagran.adk.log.LogManager

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
