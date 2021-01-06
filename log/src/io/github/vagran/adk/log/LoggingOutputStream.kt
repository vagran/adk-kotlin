/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.log

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/** Output stream to log adapter.  */
class LoggingOutputStream(private val logger: Logger,
                          private val level: LogLevel,
                          private val charset: Charset = StandardCharsets.UTF_8):
    ByteArrayOutputStream() {

    private val charBuf = StringBuilder()

    override fun flush()
    {
        var record: String

        synchronized(this) {
            super.flush()
            record = toString(charset)
            super.reset()
            charBuf.append(record)
            val sepIdx = charBuf.lastIndexOf('\n')
            if (sepIdx != -1) {
                if (sepIdx > 0) {
                    logger.Log(level, null, charBuf.substring(0, sepIdx))
                    if (sepIdx < charBuf.length - 1) {
                        charBuf.delete(0, sepIdx + 1)
                    } else {
                        charBuf.clear()
                    }
                } else {
                    charBuf.delete(0, 1)
                }
            }
        }
    }
}
