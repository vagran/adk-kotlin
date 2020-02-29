/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc.optimization

import io.github.vagran.adk.symcalc.Add
import io.github.vagran.adk.symcalc.Expression
import io.github.vagran.adk.symcalc.Mul
import io.github.vagran.adk.symcalc.Pow
import java.util.*
import kotlin.collections.ArrayList

/** Transform product to power function (e.g. x * x = x^2) */
internal object PowerProduct: Rule {

    override fun Match(e: Expression): Rule.MatchResult?
    {
        if (e.function != Mul) {
            return null
        }
        val result = MatchResultImpl()
        e.funcArgs!!.forEachIndexed {
            idx, arg ->
            if (arg.function == Pow) {
                result.Add(idx, arg.funcArgs!![0])
            } else {
                result.Add(idx, arg)
            }
        }
        return if (result.Finalize()) result else null
    }

    override fun Optimize(e: Expression, m: Rule.MatchResult): Expression
    {
        m as MatchResultImpl
        val args = ArrayList<Expression>()
        for (groupEntry in m.groups) {
            val sumArgs = ArrayList<Expression>()
            for (argIdx in groupEntry.value.indices) {
                val arg = e.funcArgs!![argIdx]
                if (arg.function == Pow) {
                    sumArgs.add(arg.funcArgs!![1])
                } else {
                    sumArgs.add(Expression(1.0))
                }
            }
            args.add(Expression(Pow, groupEntry.key, Expression(Add, *sumArgs.toTypedArray())))
        }
        for (argIdx in m.singleIndices) {
            args.add(e.funcArgs!![argIdx])
        }
        return Expression(Mul, *args.toTypedArray())
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    class Group {
        val indices = TreeSet<Int>()
    }

    class MatchResultImpl: Rule.MatchResult {
        val groups = HashMap<Expression, Group>()
        val singleIndices = TreeSet<Int>()

        fun Add(idx: Int, e: Expression)
        {
            val g = groups.computeIfAbsent(e) { Group() }
            g.indices.add(idx)
        }

        /** Check for groups which can be optimized. Remove one member groups.
         * @return true if found groups to optimize.
         */
        fun Finalize(): Boolean
        {
            val it = groups.iterator()
            while (it.hasNext()) {
                val entry = it.next()
                if (entry.value.indices.size == 1) {
                    singleIndices.addAll(entry.value.indices)
                    it.remove()
                }
            }
            return groups.isNotEmpty()
        }
    }
}
