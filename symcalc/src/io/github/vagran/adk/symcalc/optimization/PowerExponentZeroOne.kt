/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc.optimization

import io.github.vagran.adk.symcalc.Expression
import io.github.vagran.adk.symcalc.Pow

/** Power with one or zero exponent. */
internal object PowerExponentZeroOne: Rule {

    override fun Match(e: Expression): Rule.MatchResult?
    {
        if (e.function != Pow) {
            return null
        }
        val c = e.funcArgs!![1].constant ?: return null
        return if (c == 0.0 || c == 1.0) Rule.VoidMatchResult else null
    }

    override fun Optimize(e: Expression, m: Rule.MatchResult): Expression
    {
        val c = e.funcArgs!![1].constant!!
        if (c == 0.0) {
            return Expression(1.0)
        }
        return e.funcArgs[0]
    }
}
