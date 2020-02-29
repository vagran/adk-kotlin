/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc.optimization

import io.github.vagran.adk.symcalc.Expression
import io.github.vagran.adk.symcalc.Mul
import io.github.vagran.adk.symcalc.Pow

object PowerPower: Rule {

    override fun Match(e: Expression): Rule.MatchResult?
    {
        if (e.function != Pow) {
            return null
        }
        val arg = e.funcArgs!![0]
        if (arg.function != Pow) {
            return null
        }
        return Rule.ExpressionMatchResult(arg)
    }

    override fun Optimize(e: Expression, m: Rule.MatchResult): Expression
    {
        m as Rule.ExpressionMatchResult
        return Expression(Pow, m.e.funcArgs!![0], Expression(Mul, m.e.funcArgs[1], e.funcArgs!![1]))
    }
}
