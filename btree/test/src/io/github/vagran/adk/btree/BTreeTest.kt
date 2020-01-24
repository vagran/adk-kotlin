/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package io.github.vagran.adk.btree

import io.github.vagran.adk.LocalId
import io.github.vagran.adk.async.CurrentThreadContext
import io.github.vagran.adk.async.Task
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.aggregator.AggregateWith
import org.junit.jupiter.params.aggregator.ArgumentsAccessor
import org.junit.jupiter.params.aggregator.ArgumentsAggregator
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.*
import java.util.stream.Stream
import kotlin.test.assertNotNull
import kotlin.test.assertNull


class TestEntry(val value: Int):
    BTreePayload<Int> {
    override fun GetKey(): Int
    {
        return value
    }

    override fun Clone(): BTreePayload<Int>
    {
        return TestEntry(value)
    }

    override fun Hash(): Long
    {
        return value.toLong()
    }

    override fun toString(): String
    {
        return value.toString()
    }
}

interface DataProvider: Iterator<TestEntry> {
    fun Clone(): DataProvider
}

typealias ProviderFabric = () -> DataProvider

class SequentialSource(start: Int,
                       var count: Int,
                       private val step: Int = 1):
    DataProvider {

    override fun hasNext(): Boolean
    {
        return count > 0
    }

    override fun next(): TestEntry
    {
        if (count <= 0) {
            throw IndexOutOfBoundsException()
        }
        val result = curIdx
        curIdx += step
        count--
        return TestEntry(result)
    }

    override fun Clone(): DataProvider
    {
        return SequentialSource(
            curIdx,
            count,
            step
        )
    }

    private var curIdx = start
}

class ListSource(val data: List<TestEntry>): DataProvider, Iterator<TestEntry> by data.iterator()
{
    override fun Clone(): DataProvider
    {
        return ListSource(data)
    }
}

fun GetSequentialFabric(start: Int, count: Int, step: Int = 1): ProviderFabric
{
    return {
        SequentialSource(
            start,
            count,
            step
        )
    }
}

class TestEnv(val order: Int, val insertionMode: InsertionMode, val deletionMode: DeletionMode,
              val revisionMode: RevisionMode
) {

    enum class InsertionMode {
        ASCENDING,
        DESCENDING,
        SHUFFLE
    }

    enum class DeletionMode {
        NONE,
        HALF_FIRST,
        HALF_LAST,
        HALF_SHUFFLE,
        ALL_ASCENDING,
        ALL_DESCENDING,
        ALL_SHUFFLE
    }

    enum class RevisionMode {
        NO_CHANGE,
        CHANGE_BEFORE_DELETE,
        CHANGE_BEFORE_REINSERT,
        CHANGE_BEFORE_TEST
    }

    val storage =
        BTreeTestStorage<Int, TestEntry>()
    var btree = CreateTree()

    fun CreateTree(rootId: LocalId? = null): BTree<Int, TestEntry>
    {
        return BTree(
            storage,
            LocalId(),
            rootId,
            BTree.Config(
                order = order
            )
        )
    }

    suspend fun VerifyTree()
    {
        val order = btree.config.order
        suspend fun VerifyNode(node: BTree.Node<*, *>, level: Int)
        {
            assertEquals(order - 1, node.entries.size)
            assert(node.hdr.numEntries < order)
            if (level != 0) {
                assert(node.hdr.numEntries >= (order / 2) - 1)
            } else {
                assert(node.isLeaf || node.hdr.numEntries > 0)
            }
            for ((idx, e) in node.entries.withIndex()) {
                if (idx < node.hdr.numEntries) {
                    assertNotNull(e)
                } else {
                    assertNull(e)
                }
            }
            if (node.children != null) {
                assertEquals(order, node.children!!.size)
                for ((idx, childId) in node.children!!.withIndex()) {
                    if (idx <= node.hdr.numEntries) {
                        assertNotNull(childId)
                        val child = btree.storage.GetNode(childId).Await()
                        assertNotNull(child)
                        VerifyNode(child, level + 1)
                    } else {
                        assertNull(childId)
                    }
                }
            }
        }
        val rootId = btree.GetRootId()
        if (rootId != null) {
            val root = btree.storage.GetNode(rootId).Await()
            assertNotNull(root)
            VerifyNode(root, 0)
        }
    }

    suspend fun InsertData(mode: InsertionMode, data: DataProvider)
    {
        when(mode) {
            InsertionMode.ASCENDING ->
                for (e in data) {
                    btree.Insert(e)
                    VerifyTree()
                }
            InsertionMode.DESCENDING -> {
                val values = ArrayList<TestEntry>()
                data.forEachRemaining {values.add(it)}
                val it = values.listIterator(values.size)
                while (it.hasPrevious()) {
                    btree.Insert(it.previous())
                    VerifyTree()
                }
            }
            InsertionMode.SHUFFLE -> {
                val values = ArrayList<TestEntry>()
                data.forEachRemaining {values.add(it)}
                values.shuffle(Random(42))
                for (e in values) {
                    btree.Insert(e)
                    VerifyTree()
                }
            }
        }
    }

    /** @return deleted entries. */
    suspend fun DeleteData(mode: DeletionMode, data: DataProvider): List<TestEntry>
    {
        val values = ArrayList<TestEntry>()
        val deleted = ArrayList<TestEntry>()
        data.forEachRemaining {values.add(it)}
        when (mode) {
            DeletionMode.NONE -> return emptyList()
            DeletionMode.HALF_FIRST -> {
                var n = 0
                for (e in values) {
                    deleted.add(e)
                    btree.Delete(e.GetKey())
                    VerifyTree()
                    n++
                    if (n >= values.size / 2) {
                        break
                    }
                }
            }
            DeletionMode.HALF_LAST -> {
                val it = values.listIterator(values.size)
                var n = 0
                while (it.hasPrevious()) {
                    val e = it.previous()
                    deleted.add(e)
                    btree.Delete(e.GetKey())
                    VerifyTree()
                    n++
                    if (n >= values.size / 2) {
                        break
                    }
                }
            }
            DeletionMode.HALF_SHUFFLE -> {
                var n = 0
                values.shuffle(Random(42))
                for (e in values) {
                    deleted.add(e)
                    btree.Delete(e.GetKey())
                    VerifyTree()
                    n++
                    if (n >= values.size / 2) {
                        break
                    }
                }
            }
            DeletionMode.ALL_ASCENDING -> {
                for (e in values) {
                    btree.Delete(e.GetKey())
                    VerifyTree()
                }
                deleted.addAll(values)
            }
            DeletionMode.ALL_DESCENDING -> {
                val it = values.listIterator(values.size)
                while (it.hasPrevious()) {
                    val e = it.previous()
                    btree.Delete(e.GetKey())
                    VerifyTree()
                }
                deleted.addAll(values)
            }
            DeletionMode.ALL_SHUFFLE -> {
                values.shuffle(Random(42))
                for (e in values) {
                    btree.Delete(e.GetKey())
                    VerifyTree()
                }
                deleted.addAll(values)
            }
        }
        return deleted
    }

    suspend fun Populate(fabric: ProviderFabric)
    {
        InsertData(insertionMode, fabric())
        if (revisionMode >= RevisionMode.CHANGE_BEFORE_DELETE) {
            btree = CreateTree(btree.GetRootId())
        }
        val deleted = DeleteData(deletionMode, fabric())
        if (revisionMode >= RevisionMode.CHANGE_BEFORE_REINSERT) {
            btree = CreateTree(btree.GetRootId())
        }
        InsertData(insertionMode,
                   ListSource(deleted)
        )
        if (revisionMode >= RevisionMode.CHANGE_BEFORE_TEST) {
            btree = CreateTree(btree.GetRootId())
        }
    }
}

class TestEnvAggregator: ArgumentsAggregator {
    override fun aggregateArguments(args: ArgumentsAccessor, paramCtx: ParameterContext): Any
    {
        return TestEnv(
            args.getInteger(0),
            args.get(1) as TestEnv.InsertionMode,
            args.get(2) as TestEnv.DeletionMode,
            args.get(3) as TestEnv.RevisionMode
        )
    }
}

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class BTreeTest {
    @Suppress("unused")
    companion object {
        @JvmStatic
        fun BasicTestEnvParams(): Stream<Arguments>
        {
            val orders = listOf(6, 7, 8, 9)
            val insModes = TestEnv.InsertionMode.values()
            val delModes = TestEnv.DeletionMode.values()
            val revModes = TestEnv.RevisionMode.values()
            val builder = Stream.builder<Arguments>()
            for (revMode in revModes) {
                for (insMode in insModes) {
                    for (delMode in delModes) {
                        for (order in orders) {
                            builder.add(Arguments.of(order, insMode, delMode, revMode))
                        }
                    }
                }
            }
            return builder.build()
        }
    }

    lateinit var env: TestEnv

    suspend fun SequentialLookupTest(data: DataProvider)
    {
        var isFirst = true
        var lastValue: TestEntry? = null
        for (e in data) {
            if (isFirst) {
                val result = env.btree.CreateCursor().Next(e.value - 1)
                assertNotNull(result)
                assertEquals(e.value, result.value)
                isFirst = false
            }
            val result = env.btree.CreateCursor().Next(e.value)
            assertNotNull(result)
            assertEquals(e.value, result.value)
            lastValue = e
        }
        if (lastValue != null) {
            val result = env.btree.CreateCursor().Next(lastValue.value + 1)
            assertNull(result)
        } else {
            val result = env.btree.CreateCursor().Next(42)
            assertNull(result)
        }
    }

    suspend fun IterationAfterLookupTest(data: DataProvider, numIter: Int)
    {
        while (data.hasNext()) {
            val e = data.next()
            val cursor = env.btree.CreateCursor()
            val result = cursor.Next(e.value)
            assertNotNull(result)
            assertEquals(e.value, result.value)
            val ahead = data.Clone()
            var n = numIter
            while (ahead.hasNext() && n > 0) {
                val next = ahead.next()
                val nextResult = cursor.Next()
                assertNotNull(nextResult)
                assertEquals(next.value, nextResult.value)
                n--
            }
        }
    }

    suspend fun IterationTest(data: DataProvider)
    {
        val cursor = env.btree.CreateCursor()
        for (e in data) {
            val result = cursor.Next()
            assertNotNull(result)
            assertEquals(e.value, result.value)
        }
        assertNull(cursor.Next())
        assertNull(cursor.Next())
    }

    suspend fun LookupContinuationTest(data: DataProvider, skip: Int = 0)
    {
        val cursor = env.btree.CreateCursor()
        var skipCount = 0
        for (e in data) {
            if (skipCount > 0) {
                skipCount--
                continue
            }
            val result = cursor.Next(e.value)
            assertNotNull(result)
            assertEquals(e.value, result.value)

            val result2 = cursor.Next(e.value)
            assertNotNull(result2)
            assertEquals(e.value, result2.value)

            val result3 = cursor.Next(e.value - 1)
            assertNotNull(result3)
            assertEquals(e.value, result3.value)

            skipCount = skip
        }
        if (skipCount == skip) {
            assertNull(cursor.Next())
        }
    }

    suspend fun MixedLookupIterationTest(data: DataProvider,
                                         numIterationsAfterLookup: Int = 2,
                                         roundsPerCursor: Int = -1)
    {
        var numRounds = 0
        var numIterations = 0
        var cursor = env.btree.CreateCursor()
        for (e in data) {
            val result: TestEntry?
            numIterations++
            if (numIterations == 1) {
                result = cursor.Next(e.value)
            } else {
                result = cursor.Next()
                if (numIterations > numIterationsAfterLookup) {
                    numIterations = 0
                    numRounds++
                    if (roundsPerCursor != -1 && numRounds >= roundsPerCursor) {
                        numRounds = 0
                        cursor = env.btree.CreateCursor()
                    }
                }
            }

            assertNotNull(result)
            assertEquals(e.value, result.value)
        }
    }

    suspend fun BasicTests(fabric: ProviderFabric)
    {
        SequentialLookupTest(fabric())
        IterationTest(fabric())
        IterationAfterLookupTest(fabric(), 2)
        LookupContinuationTest(fabric())
        LookupContinuationTest(fabric(), 1)
        LookupContinuationTest(fabric(), 2)
        LookupContinuationTest(fabric(), 6)
        LookupContinuationTest(fabric(), 40)
        MixedLookupIterationTest(fabric(), 2, -1)
        MixedLookupIterationTest(fabric(), 2, 1)
        MixedLookupIterationTest(fabric(), 2, 2)
        MixedLookupIterationTest(fabric(), 5, 1)
        MixedLookupIterationTest(fabric(), 5, 5)
    }

    fun RunTest(func: suspend () -> Unit)
    {
        val ctx = CurrentThreadContext()
        val def = Task.CreateDef(func).Submit(ctx).result
        def.Subscribe {
            _, _ ->
            ctx.Stop()
        }
        ctx.Run()
        def.Get()
    }

    @ParameterizedTest
    @MethodSource("BasicTestEnvParams")
    fun EmptyTree(@AggregateWith(TestEnvAggregator::class) env: TestEnv)
    {
        this.env = env
        RunTest {
            val fabric =
                GetSequentialFabric(1, 0)
            env.Populate(fabric)
            BasicTests(fabric)
        }
    }

    @ParameterizedTest
    @MethodSource("BasicTestEnvParams")
    fun OneElement(@AggregateWith(TestEnvAggregator::class) env: TestEnv)
    {
        this.env = env
        RunTest {
            val fabric =
                GetSequentialFabric(1, 1)
            env.Populate(fabric)
            BasicTests(fabric)
        }
    }

    @ParameterizedTest
    @MethodSource("BasicTestEnvParams")
    fun NonFullRootLeaf(@AggregateWith(TestEnvAggregator::class) env: TestEnv)
    {
        this.env = env
        RunTest {
            val fabric =
                GetSequentialFabric(1, 3)
            env.Populate(fabric)
            BasicTests(fabric)
        }
    }

    @ParameterizedTest
    @MethodSource("BasicTestEnvParams")
    fun FullRootLeaf(@AggregateWith(TestEnvAggregator::class) env: TestEnv)
    {
        this.env = env
        RunTest {
            val fabric =
                GetSequentialFabric(1, 5)
            env.Populate(fabric)
            BasicTests(fabric)
        }
    }

    @ParameterizedTest
    @MethodSource("BasicTestEnvParams")
    fun OneLevel(@AggregateWith(TestEnvAggregator::class) env: TestEnv)
    {
        this.env = env
        RunTest {
            val fabric =
                GetSequentialFabric(1, 16)
            env.Populate(fabric)
            BasicTests(fabric)
        }
    }

    @ParameterizedTest
    @MethodSource("BasicTestEnvParams")
    fun TwoLevels(@AggregateWith(TestEnvAggregator::class) env: TestEnv)
    {
        this.env = env
        RunTest {
            val fabric =
                GetSequentialFabric(1, 50)
            env.Populate(fabric)
            BasicTests(fabric)
        }
    }

    @ParameterizedTest
    @MethodSource("BasicTestEnvParams")
    fun ThreeLevels(@AggregateWith(TestEnvAggregator::class) env: TestEnv)
    {
        this.env = env
        RunTest {
            val fabric =
                GetSequentialFabric(1, 500)
            env.Populate(fabric)
            BasicTests(fabric)
        }
    }

    @ParameterizedTest
    @MethodSource("BasicTestEnvParams")
    fun DuplicateInsert(@AggregateWith(TestEnvAggregator::class) env: TestEnv)
    {
        this.env = env
        RunTest {
            val fabric =
                GetSequentialFabric(1, 50)
            env.Populate(fabric)
            assertFalse(env.btree.Insert(
                TestEntry(
                    42
                )
            ))
        }
    }

    @ParameterizedTest
    @MethodSource("BasicTestEnvParams")
    fun DeletionTestLeafRoot(@AggregateWith(TestEnvAggregator::class) env: TestEnv)
    {
        this.env = env
        RunTest {
            val fabric =
                GetSequentialFabric(1, 5)
            env.Populate(fabric)
            assertTrue(env.btree.Delete(4))
            env.VerifyTree()
            /* Ensure it is deleted. */
            assertFalse(env.btree.Delete(4))
            env.VerifyTree()
        }
    }

    @ParameterizedTest
    @MethodSource("BasicTestEnvParams")
    fun DeletionTest(@AggregateWith(TestEnvAggregator::class) env: TestEnv)
    {
        this.env = env
        RunTest {
            val fabric =
                GetSequentialFabric(1, 50)
            env.Populate(fabric)
            assertTrue(env.btree.Delete(42))
            env.VerifyTree()
            /* Ensure it is deleted. */
            assertFalse(env.btree.Delete(42))
            env.VerifyTree()
        }
    }
}
