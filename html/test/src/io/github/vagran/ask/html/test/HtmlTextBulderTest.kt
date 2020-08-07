/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.marketminer.html.io.github.vagran.ask.html.test

import io.github.vagran.adk.html.HtmlTextBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HtmlTextBuilderTest {

    private fun Check(input: String, expected: String,
                      trimLeadingWs: Boolean,
                      trimTrailingWs: Boolean,
                      normalizeWs: Boolean)
    {
        val b = HtmlTextBuilder(trimLeadingWs, normalizeWs)
        for (c in input) {
            b.FeedChar(c.toInt())
        }
        assertEquals(expected, b.GetResult(trimTrailingWs))
    }

    @Test
    fun BasicTest()
    {
        Check("   \t\r\n\n\u000c\u000bAAA\t\nBBB CCC  \u000c\u000B", "AAA BBB CCC",
              true, true, true)
    }

    @Test
    fun KeepAllTest()
    {
        val s = "   \t\r\n\n\u000c\u000bAAA\t\nBBB CCC  \u000c\u000B"
        Check(s, s, false, false, false)
    }

    @Test
    fun KeepAndTrimTest()
    {
        Check("   \t\r\n\n\u000c\u000bAAA\t\nBBB CCC  \u000c\u000B",
              "AAA\t\nBBB CCC",
             true, true, false)
    }

    @Test
    fun NormalizeNoTrimTest()
    {
        Check("   \t\r\n\n\u000c\u000bAAA\t\nBBB CCC  \u000c\u000B",
              " AAA BBB CCC ",
              false, false, true)
    }

    @Test
    fun NormalizeTrimLeadingTest()
    {
        Check("   \t\r\n\n\u000c\u000bAAA\t\nBBB CCC  \u000c\u000B",
              "AAA BBB CCC ",
              true, false, true)
    }

    @Test
    fun NormalizeTrimTrailingTest()
    {
        Check("   \t\r\n\n\u000c\u000bAAA\t\nBBB CCC  \u000c\u000B",
              " AAA BBB CCC",
              false, true, true)
    }
}
