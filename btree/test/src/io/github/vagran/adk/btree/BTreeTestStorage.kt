/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.btree

import io.github.vagran.adk.LocalId
import io.github.vagran.adk.async.Deferred
import java.util.*

class BTreeTestStorage<TKey: Comparable<TKey>, TPayload: BTreePayload<TKey>>:
    BTreeStorage<TKey, TPayload> {

    private val storage = TreeMap<LocalId, BTree.Node<TKey, TPayload>>()

    override fun GetNodeHeader(nodeId: LocalId): Deferred<BTree.NodeHeader?>
    {
        val node = storage[nodeId]?.Clone() ?: return Deferred.ForResult(null)
        return Deferred.ForResult(node.hdr)
    }

    override fun GetNode(nodeId: LocalId): Deferred<BTree.Node<TKey, TPayload>?>
    {
        val node = storage[nodeId]?.Clone() ?: return Deferred.ForResult(null)
        return Deferred.ForResult(node)
    }

    override fun StoreNode(node: BTree.Node<TKey, TPayload>): Deferred<Unit>
    {
        storage[node.hdr.id] = node
        return Deferred.Unit()
    }

    override fun RemoveNode(nodeId: LocalId): Deferred<Unit>
    {
        if (storage.remove(nodeId) == null) {
            throw Error("Node does not exist")
        }
        return Deferred.Unit()
    }
}
