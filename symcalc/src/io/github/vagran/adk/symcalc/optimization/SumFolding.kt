/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc.optimization

import io.github.vagran.adk.symcalc.Add
import io.github.vagran.adk.symcalc.Expression
import io.github.vagran.adk.symcalc.Mul
import java.util.*
import kotlin.collections.ArrayList

/** x + 2x -> 3x
 * Folds only with constant factors.
 */
internal object SumFolding: Rule {

    override fun Match(e: Expression): Rule.MatchResult?
    {
        if (e.function != Add) {
            return null
        }
        val result = MatchResultImpl()
        e.funcArgs!!.forEachIndexed { idx, arg -> result.Add(idx, arg) }
        return if (result.Finalize()) result else null
    }

    override fun Optimize(e: Expression, m: Rule.MatchResult): Expression
    {
        m as MatchResultImpl
        val args = ArrayList<Expression>()
        for (groupEntry in m.groups) {
            args.add(Expression(Mul, Expression(groupEntry.value.factor), groupEntry.key))
        }
        for (argIdx in m.skippedIndices) {
            args.add(e.funcArgs!![argIdx])
        }
        return Expression(Add, *args.toTypedArray())
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    class Group(var index: Int) {
        var factor = 0.0
    }

    class MatchResultImpl: Rule.MatchResult {
        val groups = HashMap<Expression, Group>()
        val skippedIndices = TreeSet<Int>()

        fun Add(idx: Int, e: Expression)
        {
            if (e.constant != null) {
                skippedIndices.add(idx)
                return
            }
            var factor = 1.0
            val eKey: Expression
            if (e.function == Mul) {
                val args = ArrayList<Expression>()
                for (arg in e.funcArgs!!) {
                    if (arg.constant != null) {
                        factor *= arg.constant
                    } else {
                        args.add(arg)
                    }
                }
                eKey = if (args.size > 1) {
                    Expression(Mul, *args.toTypedArray())
                } else {
                    args[0]
                }
            } else {
                eKey = e
            }
            val g = groups.computeIfAbsent(eKey) { Group(idx) }
            g.factor += factor
            if (g.index != -1 && g.index != idx) {
                g.index = -1
            }
        }

        /** Check for groups which can be optimized. Remove one member groups.
         * @return true if found groups to optimize.
         */
        fun Finalize(): Boolean
        {
            val it = groups.iterator()
            while (it.hasNext()) {
                val entry = it.next()
                if (entry.value.index != -1) {
                    skippedIndices.add(entry.value.index)
                    it.remove()
                }
            }
            return groups.isNotEmpty()
        }
    }
}
