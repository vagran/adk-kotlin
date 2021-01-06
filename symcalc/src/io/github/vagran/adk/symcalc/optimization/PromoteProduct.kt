/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc.optimization

import io.github.vagran.adk.symcalc.Expression
import io.github.vagran.adk.symcalc.Mul

/* x * (y * z) -> x * y * z */
internal object PromoteProduct: Rule {

    override fun Match(e: Expression): Rule.MatchResult?
    {
        if (e.function != Mul) {
            return null
        }
        val result = Rule.ArgIdxMatchResult()
        e.funcArgs!!.forEachIndexed {
            idx, arg ->
            if (arg.function == Mul) {
                result.Add(idx)
            }
        }
        return if (result.indices.size != 0) result else null
    }

    override fun Optimize(e: Expression, m: Rule.MatchResult): Expression
    {
        m as Rule.ArgIdxMatchResult
        val args = ArrayList<Expression>()
        for (arg in e.funcArgs!!) {
            if (arg.function != Mul) {
                args.add(arg)
                continue
            }
            args.addAll(arg.funcArgs!!)
        }
        return Expression(Mul, *args.toTypedArray())
    }
}
