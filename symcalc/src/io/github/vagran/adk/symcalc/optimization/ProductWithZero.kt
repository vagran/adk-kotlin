/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc.optimization

import io.github.vagran.adk.symcalc.Expression
import io.github.vagran.adk.symcalc.Mul

object ProductWithZero: Rule {

    override fun Match(e: Expression): Rule.MatchResult?
    {
        if (e.function != Mul) {
            return null
        }
        e.funcArgs!!.forEach {
            if (it.constant == 0.0) {
                return Rule.VoidMatchResult
            }
        }
        return null
    }

    override fun Optimize(e: Expression, m: Rule.MatchResult): Expression
    {
        return Expression(0.0)
    }
}
