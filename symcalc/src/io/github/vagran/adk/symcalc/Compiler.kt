/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc

import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


class Compiler {

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

    fun Compile(): Program
    {
        program?.also { return it }
        if (targets.isEmpty()) {
            throw Error("No targets specified")
        }
        val program = Program()
        program.literals = literals.toDoubleArray()
        TODO()
        return program
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

    private class Node(val e: Expression,
                       val target: ResultHandle? = null) {

        val deps = ArrayList<Node>()
        /** Number of nodes which depend on this node value. */
        var dependantsCount = 0
        /** True if node is evaluated and available on the stack. */
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
}
