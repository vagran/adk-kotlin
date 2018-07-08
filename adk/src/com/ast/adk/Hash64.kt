package com.ast.adk

/** Hash calculator. Algorithm based on MurMurHash. */
@Suppress("NOTHING_TO_INLINE")
class Hash64(private val seed: Int = -0x68b84d74) {

    fun Feed(data: String)
    {
        Feed(data.toByteArray())
    }

    fun Feed(data: ByteArray)
    {
        Feed(data, 0, data.size)
    }

    fun Feed(data: ByteArray, offset: Int, length: Int)
    {
        var _offset = offset
        var _length = length
        var tailSize = totalCount % 8
        if (tailSize != 0) {
            while (_length > 0 && tailSize < 8) {
                tail[tailSize] = data[_offset]
                _offset++
                _length--
                tailSize++
                totalCount++
            }
            if (tailSize < 8) {
                return
            }
            ApplyBlock(GetBlock(tail, 0), GetBlock(tail, 4))
        }

        while (_length >= 8) {
            ApplyBlock(GetBlock(data, _offset), GetBlock(data, _offset + 4))
            _offset += 8
            _length -= 8
            totalCount += 8
        }

        var tailIdx = 0
        while (_length > 0) {
            tail[tailIdx] = data[_offset]
            tailIdx++
            _offset++
            _length--
            totalCount++
        }
    }

    /** Get hash value. Resets the calculator state. */
    fun Get(): Long
    {
        var tailSize = totalCount % 8
        if (tailSize != 0) {
            while (tailSize < 8) {
                tail[tailSize] = 0
                tailSize++
            }
            ApplyBlock(GetBlock(tail, 0), GetBlock(tail, 4))
        }

        h1 = h1 xor totalCount
        h2 = h2 xor totalCount
        h1 += h2
        h2 += h1

        h1 = h1 xor (h1 ushr 16)
        h1 *= -0x7a143595
        h1 = h1 xor (h1 ushr 13)
        h1 *= -0x3d4d51cb

        h2 = h2 xor (h2 ushr 16)
        h2 *= -0x7a143595
        h2 = h2 xor (h2 ushr 13)
        h2 *= -0x3d4d51cb

        h1 += h2
        h2 += h1

        val result = (h2.toLong() shl 32) or h1.toLong()
        h1 = seed
        h2 = seed
        totalCount = 0
        return result
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private var h1: Int = seed
    private var h2: Int = seed
    private val tail: ByteArray = ByteArray(8)
    private var totalCount = 0

    private companion object {
        const val C1: Int = 0x239b961b
        const val C2: Int = -0x54f16877
        const val C3: Int = 0x38b34ae5
    }

    private inline fun Rotate(x: Int, n: Int): Int
    {
        return (x shl n) or (x ushr (32 - n))
    }

    private inline fun GetBlock(data: ByteArray, offset: Int): Int
    {
        return (data[offset    ].toInt() and 0xff) +
            ((data[offset + 1].toInt() and 0xff) shl 8) +
            ((data[offset + 2].toInt() and 0xff) shl 16) +
            ((data[offset + 3].toInt() and 0xff) shl 24)
    }

    private inline fun ApplyBlock(block1: Int, block2: Int)
    {
        var k1 = block1 * C1
        k1 = Rotate(k1, 15)
        k1 *= C2
        h1 = h1 xor k1

        h1 = Rotate(h1, 19)
        h1 += h2
        h1 = h1 * 5 + 0x561ccd1b

        var k2 = block2 * C2
        k2 = Rotate(k2, 16)
        k2 *= C3
        h2 = h2 xor k2

        h2 = Rotate(h2, 17)
        h2 += h1
        h2 = h2 * 5 + 0x0bcaa747
    }
}
