/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc.optimization

import io.github.vagran.adk.symcalc.Expression
import io.github.vagran.adk.symcalc.Log
import io.github.vagran.adk.symcalc.Mul
import io.github.vagran.adk.symcalc.Pow

object PowerLogarithm: Rule {

    override fun Match(e: Expression): Rule.MatchResult?
    {
        if (e.function != Log) {
            return null
        }
        if (e.funcArgs!![0].function != Pow) {
            return null
        }
        return Rule.VoidMatchResult
    }

    override fun Optimize(e: Expression, m: Rule.MatchResult): Expression
    {
        return Expression(Mul, e.funcArgs!![0].funcArgs!![1],
                          Expression(Log, e.funcArgs[0].funcArgs!![0]))
    }

}
