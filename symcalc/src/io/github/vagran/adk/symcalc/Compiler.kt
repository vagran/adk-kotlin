/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc

import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

internal typealias OP = Program.Operation

class Compiler {
    data class Options(
        val storeVariablesInLocals: Boolean = false
    )

    /** Add expression to the pool of evaluation targets. Evaluating multiple expressions at once
     * can be more optimal if they share common subexpressions.
     */
    fun AddExpression(e: Expression, result: ResultHandle)
    {
        if (program != null) {
            throw Error("Already compiled")
        }
        CreateNode(e, result)
    }

    fun Compile(options: Options = Options()): Program
    {
        program?.also { return it }
        if (targets.isEmpty()) {
            throw Error("No targets specified")
        }
        val program = Program()
        program.literals = literals.toDoubleArray()
        for (node in targets.values) {
            if (!node.resolved) {
                EvaluateNode(node, program, options)
                if (node.dependantsCount > 0 &&
                    node.localSlot == -1 &&
                    node.literalSlot == -1 &&
                    (options.storeVariablesInLocals || node.e.variable == null)) {

                    node.localSlot = AllocateLocal()
                    program.AddOperation(OP.STORE_LOCAL, node.localSlot)
                }
                program.AddOperation(OP.POP)
            }
        }
        return program
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

    private class Node(val e: Expression,
                       val target: ResultHandle? = null) {

        val deps = ArrayList<Node>()
        /** Number of nodes which depend on this node value. */
        var dependantsCount = 0
        /** True if node was evaluated at least once. */
        var resolved = false
        /** Value other than -1 means that the node is evaluated and is available in the
         * corresponding local slot.
         */
        var localSlot = -1
        /** Index of allocated literal slot, -1 if not literal node. */
        var literalSlot = -1

        fun AddDep(dep: Node)
        {
            deps.add(dep)
            dep.dependantsCount++
        }
    }

    private val nodes = HashMap<Expression, Node>()
    private val targets = HashMap<ResultHandle, Node>()
    private val localSlots = BitSet()
    private var program: Program? = null
    private val literals = ArrayList<Double>()

    private fun CreateNode(e: Expression, target: ResultHandle?): Node
    {
        nodes[e]?.also { return it }
        val node = Node(e, target)
        nodes[e] = node
        if (target != null) {
            if (targets.put(target, node) != null) {
                throw Error("Duplicated target: $target")
            }
        }
        if (e.funcArgs != null) {
            for (arg in e.funcArgs) {
                node.AddDep(CreateNode(arg, null))
            }
        }
        if (e.constant != null) {
            node.literalSlot = literals.size
            literals.add(e.constant)
        }
        return node
    }

    /** Evaluate node and place its value on the stack. */
    private fun EvaluateNode(node: Node, program: Program, options: Options)
    {
        when {
            node.literalSlot != -1 -> {
                program.AddOperation(OP.LOAD_LITERAL, node.literalSlot)
            }
            node.localSlot != -1 -> {
                program.AddOperation(OP.LOAD_LOCAL, node.localSlot)
            }
            node.e.variable != null -> {
                program.AddOperation(OP.LOAD_VARIABLE, node.e.variable)
            }
            else -> {
                for (dep in node.deps) {
                    EvaluateNode(dep, program, options)
                    dep.dependantsCount--
                    if (dep.dependantsCount == 0 && dep.localSlot != -1) {
                        ReleaseLocal(dep.localSlot)
                        dep.localSlot = -1
                    }
                }
                program.AddOperation(OP.APPLY_FUNCTION, node.e.function!!, node.deps.size)
            }
        }

        if (!node.resolved && node.target != null) {
            program.AddOperation(OP.STORE_RESULT, node.target)
        }
        if (node.localSlot == -1 &&
            node.dependantsCount > 1 &&
            node.literalSlot == -1 &&
            (options.storeVariablesInLocals || node.e.variable == null)) {

            node.localSlot = AllocateLocal()
            program.AddOperation(OP.STORE_LOCAL, node.localSlot)
        }

        node.resolved = true
    }

    private fun AllocateLocal(): Int
    {
        //XXX count locals
        val idx = localSlots.nextClearBit(0)
        localSlots.set(idx)
        return idx
    }

    private fun ReleaseLocal(idx: Int)
    {
        if (!localSlots.get(idx)) {
            throw Error("Releasing non-allocated local: $idx")
        }
        localSlots.clear(idx)
    }
}
