package com.ast.adk

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UrlSanitizerTest {

    @Test
    fun Basic()
    {
        assertEquals("http://aaa:999/a/b%20+%40/c?d=e&f=g#h",
                     UrlSanitizer.Sanitize("http://aaa:999/a/b%20+%40/c?d=e&f=g#h"))

        assertEquals("http://aaa/a%20%7C/b",
                     UrlSanitizer.Sanitize("http://aaa/a |/b"))

        assertEquals("http://aaa/%D1%8F",
                     UrlSanitizer.Sanitize("http://aaa/я"))
    }
}
