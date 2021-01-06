/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.log

abstract class Appender(protected val pattern: Pattern?,
                        protected val level: LogLevel?) {

    open val envMask: EnvMask
        get() = pattern?.envMask ?: EnvMask()

    fun AppendMessage(msg: LogMessage)
    {
        if (level == null || msg.level >= level) {
            AppendMessageImpl(msg)
        }
    }

    abstract fun AppendMessageImpl(msg: LogMessage)

    open fun Close() {}
}
