/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk

/** Helper for human-readable formatting of size values.  */
object SizeFormat {

    private val suffixes = arrayOf("", "K", "M", "G", "T")

    /** Format size to display limited number of significant digits and magnitude suffix.  */
    operator fun invoke(size: Long): String
    {
        @Suppress("NAME_SHADOWING")
        var size = size
        if (size == 0L) {
            return "0"
        }
        val sb = StringBuilder()
        var exp = 0
        while (size >= 1000 && exp < 12) {
            size = (size + 5) / 10
            exp++
        }
        val dotExp = (exp + 2) / 3 * 3
        var stripping = true
        var _exp = exp
        while (size != 0L) {
            val digit = size % 10
            if (digit != 0L || !stripping || _exp >= dotExp) {
                stripping = false
                sb.append(('0'.toLong() + digit).toChar())
            }
            size /= 10
            _exp++
            if (_exp == dotExp) {
                if (!stripping) {
                    sb.append('.')
                }
            }
        }
        sb.reverse()
        sb.append(suffixes[(exp + 2) / 3])
        return sb.toString()
    }
}
