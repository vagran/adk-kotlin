/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.html

import io.github.vagran.adk.html.HtmlParser
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.*
import java.util.stream.Stream
import java.util.stream.StreamSupport

/** Wrapper for HtmlParser for providing pull workflow. */
class HtmlPullParser(private val input: InputStream,
                     encoding: Charset? = Charsets.UTF_8,
                     options: HtmlParser.Options = HtmlParser.Options(),
                     storeErrors: Boolean = options.failOnError):
    Iterable<HtmlParser.Token> {

    /** Get next token from the stream. This method should be called until EOF token is returned. It
     * should not be called after EOF is returned (exception is thrown).
     * If failOnError options is set, this method may throw ParserError in case of parsing failure.
     */
    fun NextToken(): HtmlParser.Token
    {
        while (true) {
            if (!pendingTokens.isEmpty()) {
                return pendingTokens.removeFirst()
            }
            val numRead = input.read(buf.array(), buf.position(), buf.remaining())
            if (numRead == -1) {
                val finalBuf = if (buf.position() > 0) {
                    buf.flip()
                } else {
                    null
                }
                /* Will either emit EOF token or throw an exception if already emitted. */
                parser.Finish(finalBuf)
                finalBuf?.compact()
                continue
            }
            buf.position(buf.position() + numRead)
            buf.flip()
            parser.FeedBytes(buf)
            buf.compact()
        }
    }

    override fun iterator(): Iterator<HtmlParser.Token>
    {
        return object: Iterator<HtmlParser.Token> {
            var eofSeen = false

            override fun hasNext(): Boolean
            {
                return !eofSeen
            }

            override fun next(): HtmlParser.Token
            {
                if (eofSeen) {
                    throw NoSuchElementException()
                }
                val token = NextToken()
                if (token.type == HtmlParser.Token.Type.EOF) {
                    eofSeen = true
                }
                return token
            }
        }
    }

    fun TokenStream(): Stream<HtmlParser.Token>
    {
        return StreamSupport.stream(spliterator(), false);
    }

    /** Get parsing errors log. Empty list is returned if storeErrors option is not set. */
    fun GetErrors(): List<HtmlParser.ParsingError>
    {
        if (parsingErrors == null) {
            throw IllegalStateException("Errors storing was not enabled for this instance")
        }
        return parsingErrors
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val parser = HtmlParser(this::OnToken, this::OnError, encoding, options)
    private val pendingTokens: Deque<HtmlParser.Token> = ArrayDeque()
    private val parsingErrors: MutableList<HtmlParser.ParsingError>? =
        if (storeErrors) ArrayList() else null
    private val buf = ByteBuffer.allocate(4096)

    private fun OnToken(token: HtmlParser.Token)
    {
        pendingTokens.addLast(token)
    }

    private fun OnError(error: HtmlParser.ParsingError)
    {
        parsingErrors?.add(error)
    }
}
