package io.github.vagran.adk

import java.nio.charset.StandardCharsets

/** Utility class for making real-world URLs compliant with RFC 2396, which is in turn required for
 * java.net.URI class.
 */
object UrlSanitizer {

    /** Does not try to validate URL, so does not throw exception if invalid syntax. Just do its
     * best to make it RFC 2396 compliant if possible. Actual validation is performed later in URI
     * class construction.
     */
    fun Sanitize(url: String): String
    {
        if (url.isEmpty()) {
            return url
        }
        val result = StringBuilder()
        val bytes = url.toByteArray(StandardCharsets.UTF_8)
        var state = if (bytes[0] == '/'.toByte()) State.PATH else State.SCHEME
        var schemeSepIdx = 0
        var escape = -1

        for (b in bytes) {
            val c = b.toChar()

            if (escape != -1) {
                if (c !in '0'..'9' && c !in 'a'..'f' && c !in 'A'..'F') {
                    escape = -1
                } else {
                    result.append(c)
                    escape++
                    if (escape >= 2) {
                        escape = -1
                    }
                    continue
                }
            }

            when (state) {
                State.SCHEME -> {
                    if (c == ':') {
                        state = State.SCHEME_SEP
                    }
                    result.append(c)
                }
                State.SCHEME_SEP -> {
                    schemeSepIdx++
                    if (schemeSepIdx >= 2 || c != '/') {
                        state = State.HOST
                    }
                    result.append(c)
                }
                State.HOST -> {
                    if (c == ':') {
                        state = State.PORT
                    } else if (c == '/') {
                        state = State.PATH
                    }
                    result.append(c)
                }
                State.PORT -> {
                    if (c == '/' || c !in '0'..'9') {
                        state = State.PATH
                    }
                    result.append(c)
                }
                State.PATH -> {
                    if (c == '?') {
                        state = State.QUERY
                        result.append(c)
                    } else if (c == '#') {
                        state = State.FRAGMENT
                        result.append(c)
                    } else if (c == '%') {
                        escape = 0
                        result.append(c)
                    } else if (c == '/' || IsAllowedChar(b)) {
                        result.append(c)
                    } else {
                        AppendEscape(b, result)
                    }
                }
                State.QUERY -> {
                    if (c == '#') {
                        state = State.FRAGMENT
                        result.append(c)
                    } else if (c == '%') {
                        escape = 0
                        result.append(c)
                    } else if (c == '&' || c == '=' || IsAllowedChar(b)) {
                        result.append(c)
                    } else {
                        AppendEscape(b, result)
                    }
                }
                State.FRAGMENT -> {
                    if (c == '%') {
                        escape = 0
                        result.append(c)
                    } else if (IsAllowedChar(b)) {
                        result.append(c)
                    } else {
                        AppendEscape(b, result)
                    }
                }
            }
        }

        return result.toString()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

    private enum class State {
        SCHEME,
        SCHEME_SEP,
        HOST,
        PORT,
        PATH,
        QUERY,
        FRAGMENT
    }

    private fun IsAllowedChar(c: Byte): Boolean
    {
        val _c = c.toChar()
        return (_c in '0'..'9') || (_c in 'a'..'z') || (_c in 'A'..'Z') ||
            _c == '-' || _c == '_' || _c == '.' || _c == '!' ||
            _c == '*' || _c == '+'
    }

    private fun AppendEscape(c: Byte, sb: StringBuilder)
    {
        sb.append('%')
        val cInt = c.toInt()
        val hi = (cInt shr 4) and 0xf
        if (hi < 10) {
            sb.append('0' + hi)
        } else {
            sb.append('A' + hi - 10)
        }
        val lo = cInt and 0xf
        if (lo < 10) {
            sb.append('0' + lo)
        } else {
            sb.append('A' + lo - 10)
        }
    }
}

