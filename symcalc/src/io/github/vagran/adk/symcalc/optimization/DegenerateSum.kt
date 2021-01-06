/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc.optimization

import io.github.vagran.adk.symcalc.Add
import io.github.vagran.adk.symcalc.Expression

/** Sum with one or zero arguments. */
internal object DegenerateSum: Rule {

    override fun Match(e: Expression): Rule.MatchResult?
    {
        if (e.function != Add) {
            return null
        }
        return if (e.funcArgs!!.size in 0..1) Rule.VoidMatchResult else null
    }

    override fun Optimize(e: Expression, m: Rule.MatchResult): Expression
    {
        return if (e.funcArgs!!.size == 1) {
            e.funcArgs[0]
        } else {
            Expression(0.0)
        }
    }
}
