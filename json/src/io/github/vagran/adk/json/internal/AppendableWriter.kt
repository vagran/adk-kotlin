package io.github.vagran.adk.json.internal

import java.io.Writer

internal class AppendableWriter(private val appendable: Appendable): Writer() {

    override fun write(chars: CharArray, offset: Int, length: Int)
    {
        CurrentWrite.chars = chars
        appendable.append(CurrentWrite, offset, offset + length)
    }

    override fun write(i: Int)
    {
        appendable.append(i.toChar())
    }

    override fun flush() {}
    override fun close() {}

    private object CurrentWrite: CharSequence {

        override val length: Int
            get() = chars.size

        override fun get(index: Int): Char {
            return chars[index]
        }

        lateinit var chars: CharArray

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence
        {
            return String(chars, startIndex, endIndex - startIndex)
        }
    }
}
