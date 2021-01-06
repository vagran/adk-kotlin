/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk

/** Hash calculator. Algorithm based on MurMurHash. */
// XXX Negative literals should be replaced when the Kotlin will allow hexadecimal overflow
@Suppress("NOTHING_TO_INLINE")
class Hash32(private val seed: Int = -0x68b84d74/* 0x9747b28c */) {

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
        var tailSize = totalCount % 4
        if (tailSize != 0) {
            while (_length > 0 && tailSize < 4) {
                tail[tailSize] = data[_offset]
                _offset++
                _length--
                tailSize++
                totalCount++
            }
            if (tailSize < 4) {
                return
            }
            ApplyBlock(GetBlock(tail, 0))
        }

        while (_length >= 4) {
            ApplyBlock(GetBlock(data, _offset))
            _offset += 4
            _length -= 4
            totalCount += 4
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
    fun Get(): Int
    {
        var tailSize = totalCount % 4
        if (tailSize != 0) {
            while (tailSize < 4) {
                tail[tailSize] = 0
                tailSize++
            }
            ApplyBlock(GetBlock(tail, 0))
        }

        h = h xor totalCount
        h = h xor (h ushr 16)
        h *= -0x7a143595// 0x85ebca6b
        h = h xor (h ushr 13)
        h *= -0x3d4d51cb//0xc2b2ae35

        val result = h
        h = seed
        totalCount = 0
        return result
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private var h: Int = seed
    private val tail: ByteArray = ByteArray(4)
    private var totalCount = 0

    private companion object {
        const val C1: Int = -0x3361d2af//0xcc9e2d51
        const val C2: Int = 0x1b873593
    }

    private inline fun Rotate(x: Int, n: Int): Int
    {
        return (x shl n) or (x ushr (32 - n))
    }

    private inline fun GetBlock(data: ByteArray, offset: Int): Int
    {
        return (data[offset    ].toInt() and 0xff) or
              ((data[offset + 1].toInt() and 0xff) shl 8) or
              ((data[offset + 2].toInt() and 0xff) shl 16) or
              ((data[offset + 3].toInt() and 0xff) shl 24)
    }

    private inline fun ApplyBlock(block: Int)
    {
        var k1 = block * C1
        k1 = Rotate(k1, 15)
        k1 *= C2
        h = h xor k1
        h = Rotate(h, 13)
        h = h * 5 - 0x19ab949c//+0xe6546b64
    }
}
