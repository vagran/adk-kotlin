/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.btree

import io.github.vagran.adk.LocalId
import io.github.vagran.adk.async.Deferred

/** Represents backing storage for B-tree. An implementation should be thread-safe with full
 * concurrency support.
 */
interface BTreeStorage<TKey: Comparable<TKey>, TPayload: BTreePayload<TKey>> {

    /** Fetch only header of the specified node. Null if no such node. */
    fun GetNodeHeader(nodeId: LocalId): Deferred<BTree.NodeHeader?>
    /** Fetch specified node. Null if no such node. */
    fun GetNode(nodeId: LocalId): Deferred<BTree.Node<TKey, TPayload>?>

    /** Either update the specified existing node or create a new one. */
    fun StoreNode(node: BTree.Node<TKey, TPayload>): Deferred<Unit>
    /** Remove the specified node from the storage. */
    fun RemoveNode(nodeId: LocalId): Deferred<Unit>
}
