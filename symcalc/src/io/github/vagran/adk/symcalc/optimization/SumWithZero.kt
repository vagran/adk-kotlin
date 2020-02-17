/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc.optimization

import io.github.vagran.adk.symcalc.Add
import io.github.vagran.adk.symcalc.Expression

object SumWithZero: Rule {

    override fun Match(e: Expression): Rule.MatchResult?
    {
        if (e.function != Add) {
            return null
        }
        val result = Rule.ArgIdxMatchResult()
        e.funcArgs!!.forEachIndexed {
            idx, arg ->
            if (arg.constant == 0.0) {
                result.Add(idx)
            }
        }
        return if (result.indices.size != 0) result else null
    }

    override fun Optimize(e: Expression, m: Rule.MatchResult): Expression
    {
        m as Rule.ArgIdxMatchResult
        if (m.indices.size == e.funcArgs!!.size) {
            return Expression(0.0)
        }
        var curIdx = 0
        val args = Array(e.funcArgs.size - m.indices.size) {
            _ ->
            while (m.indices.contains(curIdx)) {
                curIdx++
            }
            e.funcArgs[curIdx++]
        }
        return Expression(Add, *args)
    }
}
