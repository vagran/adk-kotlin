/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc.optimization

import io.github.vagran.adk.symcalc.*

object ExponentPower: Rule {

    override fun Match(e: Expression): Rule.MatchResult?
    {
        if (e.function != Pow) {
            return null
        }
        val arg = e.funcArgs!![0]
        if (arg.function != Exp) {
            return null
        }
        return Rule.ExpressionMatchResult(arg.funcArgs!![0])
    }

    override fun Optimize(e: Expression, m: Rule.MatchResult): Expression
    {
        m as Rule.ExpressionMatchResult
        return Expression(Exp, Expression(Mul, m.e, e.funcArgs!![1]))
    }
}
