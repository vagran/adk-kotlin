package com.ast.adk

/**
 * Based on MurmurHash port of Viliam Holub.
 */
object Hash {

    /**
     * Generates 32 bit hash from byte array of the given length and seed.
     *
     * @param data   byte array to hash
     * @param length length of the array to hash
     * @param seed   initial seed value
     * @return 32 bit hash of the given array
     */
    @JvmOverloads
    fun Hash32(data: ByteArray, length: Int, seed: Int = 0x9747b28c.toInt()): Int
    {
        // 'm' and 'r' are mixing constants generated offline.
        // They're not really 'magic', they just happen to work well.
        val m: Int = 0x5bd1e995
        val r: Int = 24

        // Initialize the hash to a random value
        var h: Int = seed xor length
        val length4: Int = length / 4

        for (i in 0 until length4) {
            val i4 = i * 4
            var k:Int = (data[i4].toInt() and 0xff) + ((data[i4 + 1].toInt() and 0xff) shl 8) +
                ((data[i4 + 2].toInt() and 0xff) shl 16) + ((data[i4 + 3].toInt() and 0xff) shl 24)
            k *= m
            k = k xor (k ushr r)
            k *= m
            h *= m
            h = h xor k
        }

        // Handle the last few bytes of the input array
        val remSize = length - length4 * 4
        if (remSize == 3) {
            h = h xor ((data[(length and 3.inv()) + 2].toInt() and 0xff) shl 16)
        }
        if (remSize >= 2) {
            h = h xor ((data[(length and 3.inv()) + 1].toInt() and 0xff) shl 8)
        }
        if (remSize >= 1) {
            h = h xor (data[length and 3.inv()].toInt() and 0xff)
        }
        h *= m

        h = h xor h.ushr(13)
        h *= m
        h = h xor h.ushr(15)

        return h
    }

    /**
     * Generates 32 bit hash from a string.
     *
     * @param text string to hash
     * @return 32 bit hash of the given string
     */
    fun Hash32(text: String): Int
    {
        val bytes = text.toByteArray()
        return Hash32(bytes, bytes.size)
    }

    /**
     * Generates 32 bit hash from a substring.
     *
     * @param text   string to hash
     * @param from   starting index
     * @param length length of the substring to hash
     * @return 32 bit hash of the given string
     */
    fun Hash32(text: String, from: Int, length: Int): Int
    {
        return Hash32(text.substring(from, from + length))
    }

    /**
     * Generates 64 bit hash from byte array of the given length and seed.
     *
     * @param data   byte array to hash
     * @param length length of the array to hash
     * @param seed   initial seed value
     * @return 64 bit hash of the given array
     */
    @JvmOverloads
    fun Hash64(data: ByteArray, length: Int, seed: Int = -0x1e85eb9b): Long
    {
        val m: Long = -0x395b586ca42e166bL
        val r: Int = 47

        var h: Long = (seed.toLong() and 0xffffffffL) xor length * m

        val length8: Int = length / 8

        for (i in 0 until length8) {
            val i8 = i * 8
            var k: Long =
                ((data[i8].toLong() and 0xff) + ((data[i8 + 1].toLong() and 0xff) shl 8)
                + ((data[i8 + 2].toLong() and 0xff) shl 16) + ((data[i8 + 3].toLong() and 0xff) shl 24)
                + ((data[i8 + 4].toLong() and 0xff) shl 32) + ((data[i8 + 5].toLong() and 0xff) shl 40)
                + ((data[i8 + 6].toLong() and 0xff) shl 48) + ((data[i8 + 7].toLong() and 0xff) shl 56))

            k *= m
            k = k xor (k ushr r)
            k *= m

            h = h xor k
            h *= m
        }

        val remSize = length - length8 * 8
        if (remSize == 7) {
            h = h xor ((data[(length and 7.inv()) + 6].toInt() and 0xff).toLong() shl 48)
        }
        if (remSize >= 6) {
            h = h xor ((data[(length and 7.inv()) + 5].toInt() and 0xff).toLong() shl 40)
        }
        if (remSize >= 5) {
            h = h xor ((data[(length and 7.inv()) + 4].toInt() and 0xff).toLong() shl 32)
        }
        if (remSize >= 4) {
            h = h xor ((data[(length and 7.inv()) + 3].toInt() and 0xff).toLong() shl 24)
        }
        if (remSize >= 3) {
            h = h xor ((data[(length and 7.inv()) + 2].toInt() and 0xff).toLong() shl 16)
        }
        if (remSize >= 2) {
            h = h xor ((data[(length and 7.inv()) + 1].toInt() and 0xff).toLong() shl 8)
        }
        if (remSize >= 1) {
            h = h xor (data[length and 7.inv()].toInt() and 0xff).toLong()
        }
        h *= m

        h = h xor (h ushr r)
        h *= m
        h = h xor (h ushr r)

        return h
    }

    /**
     * Generates 64 bit hash from a string.
     *
     * @param text string to hash
     * @return 64 bit hash of the given string
     */
    fun Hash64(text: String): Long
    {
        val bytes = text.toByteArray()
        return Hash64(bytes, bytes.size)
    }

    /**
     * Generates 64 bit hash from a substring.
     *
     * @param text   string to hash
     * @param from   starting index
     * @param length length of the substring to hash
     * @return 64 bit hash of the given array
     */
    fun Hash64(text: String, from: Int, length: Int): Long
    {
        return Hash64(text.substring(from, from + length))
    }
}
