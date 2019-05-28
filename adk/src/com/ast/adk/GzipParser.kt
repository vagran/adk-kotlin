package com.ast.adk

import java.io.ByteArrayOutputStream
import java.lang.StringBuilder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.Inflater
import kotlin.math.min

@Suppress("SpellCheckingInspection")
class GzipParser(val outputHandler: (ByteBuffer) -> Unit) {
    val isHeaderProcessed: Boolean get() = state >= State.DATA
    val fileName: String? get() = _fileName
    val fileComment: String? get() = _fileComment
    val mtime: Long get() = _mtime
    val os: Int get() = _os

    class ParseError(message: String): Exception(message)

    fun FeedBytes(buf: ByteBuffer)
    {
        var done: Boolean
        do {
            done = when (state) {
                State.ID -> HandleIdState(buf)
                State.CM -> HandleCmState(buf)
            }
        } while (!done)
    }

    fun FeedBytes(buf: ByteArray, offset: Int = 0, size: Int = buf.size - offset)
    {
        val bbuf = ByteBuffer.wrap(buf, offset, size)
        FeedBytes(bbuf)
        if (bbuf.hasRemaining()) {
            AppendInputBuf(bbuf)
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private var _fileName: String? = null
    private var _fileComment: String? = null
    private val crc = CRC32()
    private val inf = Inflater()
    private var state = State.ID
    private val inputBuf = ByteArray(4)
    private var inputBufOffset = 0
    private var inputBufSize = 0
    private var flags = 0
    private var _mtime: Long = 0
    private var xfl: Int = 0
    private var _os: Int = 0
    private var xlen: Int = 0
    private var sb: ByteArrayOutputStream? = null
    private val outBuf = ByteBuffer.allocate(4096)

    private enum class State {
        ID,
        CM,
        FLG,
        MTIME,
        XFL,
        OS,
        XLEN,
        EXTRA_FIELD,
        FILE_NAME,
        FILE_COMMENT,
        HEADER_CRC,
        DATA,
        DATA_CRC,
        INPUT_SIZE
    }

    companion object {
        const val FILE_MAGIC: Int = 0x8b1f
        const val FTEXT = 1    // Text data
        const val FHCRC = 2    // Header CRC
        const val FEXTRA = 4   // Extra field
        const val FNAME = 8    // File name
        const val FCOMMENT = 16// File comment
    }

    private fun AppendInputBuf(buf: ByteBuffer)
    {
        if (buf.remaining() > inputBuf.size - inputBufSize) {
            throw Error("Unexpectedly long reminder in a buffer")
        }
        buf.get(inputBuf, inputBufSize, buf.remaining())
    }

    private fun ReadUInt(buf: ByteBuffer): Long
    {
        if (inputBufSize + buf.remaining() < 4) {
            return -1
        }
        return ReadByte(buf).toLong() or (ReadByte(buf).toLong() shl 8) or
            (ReadByte(buf).toLong() shl 16) or (ReadByte(buf).toLong() shl 24)
    }

    private fun ReadUShort(buf: ByteBuffer): Int
    {
        if (inputBufSize + buf.remaining() < 2) {
            return -1
        }
        return ReadByte(buf) or (ReadByte(buf) shl 8)
    }

    private fun ReadByte(buf: ByteBuffer): Int
    {
        val result: Int
        if (inputBufSize != 0) {
            result = inputBuf[inputBufOffset].toInt()
            inputBufOffset++
            if (inputBufOffset == inputBufSize) {
                inputBufOffset = 0
                inputBufSize = 0
            }
        } else {
            if (!buf.hasRemaining()) {
                return -1
            }
            result = buf.get().toInt()
        }
        crc.update(result)
        return result
    }

    private fun HandleIdState(buf: ByteBuffer): Boolean
    {
        val id = ReadUShort(buf)
        if (id == -1) {
            return false
        }
        if (id != FILE_MAGIC) {
            throw ParseError("Bad file magic")
        }
        state = State.CM
        return true
    }

    private fun HandleCmState(buf: ByteBuffer): Boolean
    {
        val cm = ReadByte(buf)
        if (cm == -1) {
            return false
        }
        if (cm != 8) {
            throw ParseError("Unsupported compression method")
        }
        state = State.FLG
        return true
    }

    private fun HandleFlgState(buf: ByteBuffer): Boolean
    {
        val flags = ReadByte(buf)
        if (flags == -1) {
            return false
        }
        this.flags = flags
        state = State.MTIME
        return true
    }

    private fun HandleMtimeState(buf: ByteBuffer): Boolean
    {
        val mtime = ReadUInt(buf)
        if (mtime == -1L) {
            return false
        }
        _mtime = mtime
        state = State.XFL
        return true
    }

    private fun HandleXflState(buf: ByteBuffer): Boolean
    {
        val xfl = ReadByte(buf)
        if (xfl == -1) {
            return false
        }
        this.xfl = xfl
        state = State.OS
        return true
    }

    private fun HandleOsState(buf: ByteBuffer): Boolean
    {
        val os = ReadByte(buf)
        if (os == -1) {
            return false
        }
        _os = os
        state = State.XLEN
        return true
    }

    private fun HandleXlenState(buf: ByteBuffer): Boolean
    {
        val xlen = ReadUShort(buf)
        if (xlen == -1) {
            return false
        }
        this.xlen = xlen
        state = State.EXTRA_FIELD
        return true
    }

    private fun HandleExtraFieldState(buf: ByteBuffer): Boolean
    {
        while (xlen > 0) {
            if (ReadByte(buf) == -1) {
                return false
            }
            xlen--
        }
        state = State.FILE_NAME
        return true
    }

    private fun HandleFileNameState(buf: ByteBuffer): Boolean
    {
        if ((flags and FNAME) != 0) {
            val sb = this.sb ?: ByteArrayOutputStream().also { this.sb = it }
            while (true) {
                val b = ReadByte(buf)
                if (b == -1) {
                    return false
                }
                if (b == 0) {
                    break
                }
                sb.write(b)
            }
            _fileName = sb.toString(StandardCharsets.UTF_8)
            sb.reset()
        }
        state = State.FILE_COMMENT
        return true
    }

    private fun HandleFileCommentState(buf: ByteBuffer): Boolean
    {
        if ((flags and FCOMMENT) != 0) {
            val sb = this.sb ?: ByteArrayOutputStream().also { this.sb = it }
            while (true) {
                val b = ReadByte(buf)
                if (b == -1) {
                    return false
                }
                if (b == 0) {
                    break
                }
                sb.write(b)
            }
            _fileComment = sb.toString(StandardCharsets.UTF_8)
            sb.reset()
        }
        state = State.HEADER_CRC
        return true
    }

    private fun HandleHeaderCrcState(buf: ByteBuffer): Boolean
    {
        if ((flags and FHCRC) != 0) {
            val cs = crc.value.toInt() and 0xffff
            val hcs = ReadUShort(buf)
            if (hcs == -1) {
                return false
            }
            if (cs != hcs) {
                throw ParseError("Header checksum mismatch")
            }
        }
        crc.reset()
        state = State.DATA
        return true
    }

    private fun HandleDataState(buf: ByteBuffer): Boolean
    {
        if (inputBufOffset != 0 || inputBufSize != 0) {
            throw Error("Unexpected input buffer content")
        }
        inf.setInput(buf)
        //XXX
    }
}
