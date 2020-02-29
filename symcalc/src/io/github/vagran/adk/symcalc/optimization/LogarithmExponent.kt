/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc.optimization

import io.github.vagran.adk.symcalc.Exp
import io.github.vagran.adk.symcalc.Expression
import io.github.vagran.adk.symcalc.Log

/** exp(ln(x)) -> x */
internal object LogarithmExponent: Rule {

    override fun Match(e: Expression): Rule.MatchResult?
    {
        if (e.function != Exp) {
            return null
        }
        val arg = e.funcArgs!![0]
        if (arg.function != Log) {
            return null
        }
        return Rule.ExpressionMatchResult(arg.funcArgs!![0])
    }

    override fun Optimize(e: Expression, m: Rule.MatchResult): Expression
    {
        return (m as Rule.ExpressionMatchResult).e
    }
}
