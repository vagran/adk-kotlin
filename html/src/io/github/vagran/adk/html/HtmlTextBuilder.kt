/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.html

/**
 * @param trimLeadingWs Trim all leading whitespaces.
 * @param trimTrailingWs Trim all trailing whitespaces.
 * @param normalizeWs Replace all whitespace sequences with a single space.
 */
class HtmlTextBuilder(private val trimLeadingWs: Boolean,
                      private val normalizeWs: Boolean) {

    private var buf: StringBuilder? = null

    fun FeedString(s: CharSequence)
    {
        if (s.length == 1) {
            FeedChar(s[0].toInt())
        } else {
            s.codePoints().forEach { c -> FeedChar(c) }
        }
    }

    fun FeedChar(c: Int)
    {
        val isWs = Character.isWhitespace(c)
        if (trimLeadingWs) {
            if (buf == null && isWs) {
                return
            }
        }
        if (!normalizeWs) {
            AppendChar(c, isWs)
            return
        }
        if (isWs) {
            if (lastWsStartIdx == -1) {
                AppendChar(0x20, true)
            }
        } else {
            AppendChar(c, false)
        }
    }

    fun GetResult(trimTrailingWs: Boolean): String
    {
        if (trimTrailingWs && buf != null && lastWsStartIdx != -1) {
            buf!!.setLength(lastWsStartIdx)
        }
        return if (buf == null) { "" } else { buf.toString() }
    }

    fun IsEmpty(): Boolean = buf == null

    // /////////////////////////////////////////////////////////////////////////////////////////////
    /** Last index of whitespace characters sequence start in the buffer. */
    private var lastWsStartIdx: Int = -1

    private fun AppendChar(c: Int, isWs: Boolean)
    {
        if (buf == null) {
            buf = StringBuilder()
        }
        if (isWs) {
            if (lastWsStartIdx == -1) {
                lastWsStartIdx = buf!!.length
            }
        } else {
            lastWsStartIdx = -1
        }
        buf!!.appendCodePoint(c)
    }
}
