/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc


class Compiler {

    /** Add expression to pool of evaluation targets. Evaluating multiple expressions at once can be
     * more optimal if they share common subexpressions.
     */
    fun AddExpression(e: Expression, result: ResultHandle)
    {
        TODO()
    }

    fun Compile(): Program
    {
        TODO()
    }
}
