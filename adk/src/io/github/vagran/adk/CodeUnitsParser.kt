package io.github.vagran.adk

/**
 * Combines input code units to code points.
 * @param codePointHandler Called for each found code point. -1 is passed for EOF.
 * @param errorHandler Called for each parsing error. The error is recovered if the handler is
 *  specified, otherwise ParseException is thrown.
 */
class CodeUnitsParser(private val codePointHandler: (codePoint: Int) -> Unit,
                      private val errorHandler: ((message: String) -> Unit)? = null) {

    class ParseException(message: String): Exception(message)

    /** @param c Next code unit, -1 for EOF. */
    fun Feed(c: Int)
    {
        if (pendingUnit == -1) {
            if (c == -1) {
                codePointHandler(-1)
                return
            }
            val hiC = c.toChar()
            if (Character.isLowSurrogate(hiC)) {
                ParsingError("Low surrogate unit encountered before high surrogate")
                return
            }
            if (!Character.isHighSurrogate(hiC)) {
                codePointHandler(c)
                return
            }
            pendingUnit = c
            return
        }

        val hi = pendingUnit
        val hiC = hi.toChar()
        pendingUnit = -1

        if (c == -1) {
            ParsingError("Unterminated high surrogate at EOF")
            codePointHandler(-1)
            return
        }
        val loC = c.toChar()
        if (!Character.isLowSurrogate(loC)) {
            ParsingError("Low surrogate not found after high surrogate")
            if (Character.isHighSurrogate(hiC)) {
                pendingUnit = c
            } else {
                codePointHandler(c)
            }
            return
        }
        codePointHandler(Character.toCodePoint(hiC, loC))
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private var pendingUnit: Int = -1

    private fun ParsingError(message: String)
    {
        errorHandler?.also {
            it(message)
            return
        }
        throw ParseException(message)
    }
}
