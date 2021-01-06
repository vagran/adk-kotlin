/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc.optimization

import io.github.vagran.adk.symcalc.Add
import io.github.vagran.adk.symcalc.ConstantEvaluationContext
import io.github.vagran.adk.symcalc.Expression

/* Fold all constants in a sum. */
internal object SumWithConstants: Rule {

    override fun Match(e: Expression): Rule.MatchResult?
    {
        if (e.function != Add) {
            return null
        }
        val result = Rule.ArgIdxMatchResult()
        e.funcArgs!!.forEachIndexed {
            idx, arg ->
            if (arg.constant != null) {
                result.Add(idx)
            }
        }
        return if (result.indices.size >= 2) result else null
    }

    override fun Optimize(e: Expression, m: Rule.MatchResult): Expression
    {
        m as Rule.ArgIdxMatchResult
        if (m.indices.size == e.funcArgs!!.size) {
            return Expression(e.Evaluate(ConstantEvaluationContext))
        }
        var curIdx = 0
        var sum = 0.0
        val args = Array(e.funcArgs.size - m.indices.size + 1) {
            while (m.indices.contains(curIdx)) {
                sum += e.funcArgs[curIdx].constant!!
                curIdx++
            }
            if (curIdx == e.funcArgs.size) {
                return@Array Expression(sum)
            }
            e.funcArgs[curIdx++]
        }
        return Expression(Add, *args)
    }
}
