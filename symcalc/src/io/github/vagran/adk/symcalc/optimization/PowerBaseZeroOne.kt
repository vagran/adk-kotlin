/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc.optimization

import io.github.vagran.adk.symcalc.Expression
import io.github.vagran.adk.symcalc.Pow

object PowerBaseZeroOne: Rule {

    override fun Match(e: Expression): Rule.MatchResult?
    {
        if (e.function != Pow) {
            return null
        }
        val c = e.funcArgs!![0].constant ?: return null
        return if (c == 0.0 || c == 1.0) Rule.VoidMatchResult else null
    }

    override fun Optimize(e: Expression, m: Rule.MatchResult): Expression
    {
        val c = e.funcArgs!![0].constant!!
        if (c == 0.0) {
            return Expression(0.0)
        }
        return Expression(1.0)
    }

}
