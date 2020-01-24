/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.btree

/** Comparable interface should be implemented for comparing key of the payload. */
interface BTreePayload<TKey: Comparable<TKey>> {

    fun GetKey(): TKey
    fun Clone(): BTreePayload<TKey>
    //XXX is it needed 64 bits?
    fun Hash(): Long
}
