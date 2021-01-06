/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.kdtree

import io.github.vagran.adk.Random
import io.github.vagran.adk.math.Vector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*
import java.util.stream.Collectors
import kotlin.collections.HashMap

const val vectorSize = 5

internal class Node {
    val coord = Vector.Zero(vectorSize)
    var id = 0L

    fun IsInRadius(pt: Vector, radius: Double): Boolean
    {
        return pt.DistanceSquared(coord) <= radius * radius
    }
}

private fun FindInRadius(pt: Vector, radius: Double,
                         nodes: Collection<Node>): Map<Long, Node>
{
    val result = HashMap<Long, Node>()
    for (node in nodes) {
        if (node.IsInRadius(pt, radius)) {
            result[node.id] = node
        }
    }
    return result
}

@Suppress("UNCHECKED_CAST", "MapGetWithNotNullAssertionOperator")
private fun GetSortedNodes(pt: Vector,
                           nodes: Map<Long, Node>): Array<Long>
{
    val result = arrayOfNulls<Long>(nodes.size)
    for ((idx, id) in nodes.keys.withIndex()) {
        result[idx] = id
    }
    Arrays.sort<Long>(result) { id1: Long, id2: Long ->

        val node1 = nodes[id1]!!
        val node2 = nodes[id2]!!
        val dist1 = node1.coord.DistanceSquared(pt)
        val dist2 = node2.coord.DistanceSquared(pt)
        dist1.compareTo(dist2)
    }
    return result as Array<Long>
}

private fun VerifyResult(expected: Map<Long, Node>,
                         actual: List<Node>)
{
    assertEquals(expected.size, actual.size)
    for (node in actual) {
        assertTrue(expected.containsKey(node.id))
    }
}

private fun VerifyKnnResult(expectedIds: Array<Long>, numExpected: Int,
                            actual: List<Node>)
{
    assertEquals(numExpected, actual.size)
    for (i in 0 until numExpected) {
        assertEquals(expectedIds[i], actual[i].id)
    }
}

private fun GetRandomVector(rnd: Random): Vector
{
    val v = DoubleArray(vectorSize)
    for (i in 0 until vectorSize) {
        v[i] = rnd.GetDouble()
    }
    return Vector(v)
}

private fun TestRadiusSearch(rnd: Random,
                             nodes: Map<Long, Node>,
                             tree: KdTree<Node>)
{
    println("Radius-search test...")
    /* Find nodes in radius for randomly selected coordinates. */
    for (i in 0..49) {
        val radius: Double = 0.4 * rnd.GetDouble()
        val pt = GetRandomVector(rnd)
        val expected = FindInRadius(pt, radius, nodes.values)
        val result = tree.FindInRadius(pt, radius)
        VerifyResult(expected, result.stream()
            .map<Node> { it.data }
            .collect(Collectors.toList<Node>()))
    }
}

private fun TestKnnSearch(rnd: Random,
                          nodes: Map<Long, Node>,
                          tree: KdTree<Node>)
{
    println("kNN-search test...")
    /* Test kNN-search. */
    for (i in 0..19) {
        val pt = GetRandomVector(rnd)
        val sortedIds = GetSortedNodes(pt, nodes)
        val result =  tree.FindKnn(pt, 20)
        VerifyKnnResult(sortedIds, 20, result.stream()
            .map<Node> { it.data }
            .collect(Collectors.toList<Node>()))
    }
}

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class KdTreeTest {

    @Test
    fun BasicFunctionality()
    {
        val tree = KdTree<Node>(vectorSize, 8)
        val rnd = Random()
        val nodes = HashMap<Long, Node>()
        var numPoints = 100000

        for (i in 0 until numPoints) {
            val node = Node()
            node.coord.Set(GetRandomVector(rnd))
            node.id = tree.Add(node.coord, node)
            nodes[node.id] = node
        }

        assertEquals(numPoints, tree.size)

        TestRadiusSearch(rnd, nodes, tree)

        TestKnnSearch(rnd, nodes, tree)

        println("Deleting nodes...")
        for (i in 0 until numPoints / 2) {
            val id = nodes.keys.iterator().next()
            nodes.remove(id)
            tree.Delete(id)
        }
        numPoints -= numPoints / 2

        assertEquals(numPoints, tree.size)

        TestRadiusSearch(rnd, nodes, tree)

        TestKnnSearch(rnd, nodes, tree)

        tree.Clear()
        assertEquals(0, tree.size)
    }
}
