/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.fail
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.min
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GzipParserTest {

    @Suppress("SpellCheckingInspection")
    val testData = """
Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore
et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut
aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse
cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in
culpa qui officia deserunt mollit anim id est laborum. Эюя
"""

    private fun GetCompressedData(): ByteArray
    {
        val result = ByteArrayOutputStream()
        val gzip = GZIPOutputStream(result)
        gzip.write(testData.toByteArray(StandardCharsets.UTF_8))
        gzip.finish()
        return result.toByteArray()
    }

    private fun FeedData(parser: GzipParser, data: ByteArray, chunkSize: Int = 32)
    {
        var offset = 0
        while (offset < data.size) {
            val n = min(chunkSize, data.size - offset)
            parser.FeedBytes(offset + n == data.size, data, offset, n)
            offset += n
        }
    }

    private fun FeedDataBuffers(parser: GzipParser, data: ByteArray, chunkSize: Int = 32)
    {
        var offset = 0
        var readOffset = 0
        while (offset < data.size) {
            val n = min(chunkSize, data.size - offset)
            val buf = ByteBuffer.wrap(data, readOffset, n + offset - readOffset)
            val startPos = buf.position()
            parser.FeedBytes(buf, offset + n == data.size)
            readOffset += buf.position() - startPos
            offset += n
        }
    }

    inner class Verifier {
        private val buf = ByteArrayOutputStream()
        private var isComplete = false

        fun Feed(data: ByteBuffer, isLast: Boolean)
        {
            if (isComplete) {
                fail("Data fed after last chunk")
            }
            buf.write(data.array(), data.position() + data.arrayOffset(), data.remaining())
            data.position(data.position() + data.remaining())
            if (isLast) {
                isComplete = true
                Verify()
            }
        }

        fun Finish()
        {
            if (!isComplete) {
                fail("Last chunk not provided")
            }
        }

        private fun Verify()
        {
            assertEquals(testData, buf.toString(StandardCharsets.UTF_8))
        }
    }

    @Test
    fun SingleByteArrays()
    {
        val verifier = Verifier()
        val parser = GzipParser(verifier::Feed)
        FeedData(parser, GetCompressedData(), 1)
        verifier.Finish()
    }

    @Test
    fun SmallArrays()
    {
        val verifier = Verifier()
        val parser = GzipParser(verifier::Feed)
        FeedData(parser, GetCompressedData(), 3)
        verifier.Finish()
    }

    @Test
    fun MediumArrays()
    {
        val verifier = Verifier()
        val parser = GzipParser(verifier::Feed)
        FeedData(parser, GetCompressedData())
        verifier.Finish()
    }

    @Test
    fun LargeArrays()
    {
        val verifier = Verifier()
        val parser = GzipParser(verifier::Feed)
        FeedData(parser, GetCompressedData(), 4096)
        verifier.Finish()
    }

    @Test
    fun SingleByteBuffers()
    {
        val verifier = Verifier()
        val parser = GzipParser(verifier::Feed)
        FeedDataBuffers(parser, GetCompressedData(), 1)
        verifier.Finish()
    }

    @Test
    fun SmallBuffers()
    {
        val verifier = Verifier()
        val parser = GzipParser(verifier::Feed)
        FeedDataBuffers(parser, GetCompressedData(), 3)
        verifier.Finish()
    }

    @Test
    fun MediumBuffers()
    {
        val verifier = Verifier()
        val parser = GzipParser(verifier::Feed)
        FeedDataBuffers(parser, GetCompressedData())
        verifier.Finish()
    }

    @Test
    fun LargeBuffers()
    {
        val verifier = Verifier()
        val parser = GzipParser(verifier::Feed)
        FeedDataBuffers(parser, GetCompressedData(), 4096)
        verifier.Finish()
    }

    @Test
    fun BuiltinParser()
    {
        val verifier = Verifier()
        val data = ByteArrayInputStream(GetCompressedData())
        val gzip = GZIPInputStream(data)
        val decompressed = gzip.readAllBytes()
        verifier.Feed(ByteBuffer.wrap(decompressed), true)
        verifier.Finish()
    }
}
