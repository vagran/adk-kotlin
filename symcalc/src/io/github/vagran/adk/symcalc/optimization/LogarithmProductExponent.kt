/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc.optimization

import io.github.vagran.adk.symcalc.Exp
import io.github.vagran.adk.symcalc.Expression
import io.github.vagran.adk.symcalc.Log
import io.github.vagran.adk.symcalc.Mul

/** exp(a * b * ln(x)) -> x^(a * b) */
internal object LogarithmProductExponent: Rule {

    override fun Match(e: Expression): Rule.MatchResult?
    {
        if (e.function != Exp) {
            return null
        }
        val arg = e.funcArgs!![0]
        if (arg.function != Mul) {
            return null
        }
        var base: Expression? = null
        val result = MatchResultImpl()
        for (expArg in arg.funcArgs!!) {
            if (expArg.function == Log) {
                if (base != null) {
                    /* Only one logarithm allowed. */
                    return null
                }
                base = expArg.funcArgs!![0]
            } else {
                result.exp.add(expArg)
            }
        }
        if (result.exp.size < 1) {
            return null
        }
        if (base == null) {
            return null
        }
        result.base = base
        return result
    }

    override fun Optimize(e: Expression, m: Rule.MatchResult): Expression
    {
        m as MatchResultImpl
        return if (m.exp.size > 1) {
            m.base pow (Expression(Mul, *m.exp.toTypedArray()))
        } else {
            m.base pow m.exp[0]
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    class MatchResultImpl: Rule.MatchResult {
        val exp = ArrayList<Expression>()
        lateinit var base: Expression
    }
}
