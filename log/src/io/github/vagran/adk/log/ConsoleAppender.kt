/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.log

class ConsoleAppender(config: LogConfiguration.Appender):
    Appender(GetPattern(config), config.level) {

    override fun AppendMessageImpl(msg: LogMessage)
    {
        stream.println(pattern!!.FormatMessage(msg))
        msg.exception?.also {
            it.printStackTrace(stream)
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val stream = when (config.consoleParams!!.target) {
        LogConfiguration.Appender.ConsoleParams.Target.STDOUT -> System.out
        LogConfiguration.Appender.ConsoleParams.Target.STDERR -> System.err
    }

    private companion object {
        fun GetPattern(config: LogConfiguration.Appender): Pattern
        {
            return if (config.pattern == null) {
                Pattern(LogConfiguration.DEFAULT_PATTERN)
            } else {
                Pattern(config.pattern!!)
            }
        }
    }
}
