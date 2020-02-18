/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc

interface EvaluationContext {

    fun GetVariable(v: Variable): Double
}

/** Used for evaluating constant expressions. */
object ConstantEvaluationContext: EvaluationContext {
    override fun GetVariable(v: Variable): Double
    {
        throw Error("Variable $v value requested during constant expression evaluation")
    }
}
