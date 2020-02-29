/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc.optimization

import io.github.vagran.adk.symcalc.Expression
import java.util.*
import kotlin.collections.ArrayList

interface Rule {

    interface MatchResult

    object VoidMatchResult: MatchResult

    class ArgIdxMatchResult: MatchResult {
        val indices = TreeSet<Int>()

        fun Add(idx: Int)
        {
            indices.add(idx)
        }
    }

    class ExpressionMatchResult(val e: Expression): MatchResult

    /** Try to match the rule against the provided expression.
     * @return Rule-specific match result if any, null if no match.
     */
    fun Match(e: Expression): MatchResult?

    /** Optimize the provided expression.
     * @return Optimized expression.
     */
    fun Optimize(e: Expression, m: MatchResult): Expression

    companion object {
        val builtinRules: List<Rule>

        init {
            builtinRules = ArrayList<Rule>().apply {
                add(PromoteSum)
                add(DegenerateSum)
                add(SumWithZero)
                add(SumWithConstants)

                add(PromoteProduct)
                add(DegenerateProduct)
                add(ProductWithOne)
                add(ProductWithZero)
                add(ProductWithConstants)

                add(PowerExponentZeroOne)
                add(PowerBaseZeroOne)

                add(PowerProduct)
                add(PowerLogarithm)
                add(ExponentLogarithm)
                add(LogarithmExponent)
                add(ExponentPower)
                add(PowerPower)
            }
        }
    }
}
