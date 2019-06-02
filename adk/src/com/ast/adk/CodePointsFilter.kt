package com.ast.adk

/** Performs some common filtering useful in various parsers. Maintains line and column counters.
 * @param codePointHandler Filtered code point handler. -1 is passed for EOF.
 * @param errorHandler Called for each parsing error. The error is recovered if the handler is
 *  specified, otherwise ParseException is thrown.
 * @param charValidator Validates each input codepoint if specified. Error is generated if false
 *  result is returned.
 */
class CodePointsFilter(private val codePointHandler: (codePoint: Int) -> Unit,
                       private val errorHandler: ((message: String) -> Unit)? = null,
                       private val charValidator: ((c: Int) -> Boolean)? = null) {
    val curLine get() = _curLine
    val curCol get() = _curCol

    class ParseException(message: String): Exception(message)

    /** Feed next code point, -1 for EOF. */
    fun Feed(c: Int)
    {
        if (charValidator != null && !charValidator.invoke(c)) {
            ParsingError("Illegal character encountered")
            return
        }
        if (c == -1) {
            codePointHandler(-1)
            return
        }
        if (c == 0) {
            return
        }
        if (c == 0xd) {
            lastCharCr = true
            _curLine++
            _curCol = 0
            codePointHandler(0xa)
            return
        }
        if (c == 0xa && lastCharCr) {
            lastCharCr = false
            return
        }
        if (c == 0xa) {
            _curLine++
            _curCol = 0
        } else {
            _curCol++
        }
        lastCharCr = false
        codePointHandler(c)
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private var _curLine = 1
    private var _curCol = 0
    /** Set if last encountered character was CR. */
    private var lastCharCr = false

    private fun ParsingError(message: String)
    {
        errorHandler?.also {
            it(message)
            return
        }
        throw ParseException(message)
    }
}
