/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

import {PostRequest} from "./utils"

export class EventBusSubscription {

    /** @param url URL the EventBus DCO is mapped on.
     * @param topics {String|[String]} List of topics to subscribe on. May be single topic as well.
     */
    constructor(url, topics) {
        this._url = url
        this._curSeq = -1
        if (Array.isArray(topics)) {
            this._topics = topics
        } else {
            this._topics = [topics]
        }
        this._queue = null
        this._queueIdx = 0
        this._abortCtrl = new AbortController()
    }

    /** Abort current fetch if any. Concurrent GetNext() call is failed if any. */
    Destroy() {
        this._abortCtrl.abort()
        this._queue = null
    }

    /**
     * Get next event when available.
     * @return {Promise<{seq, topic, event}>}
     */
    async GetNext() {
        if (this._queue != null) {
            return this._GetNextFromQueue()
        }
        while (true) {
            const q = await PostRequest(this._url + "/PollEvents", {
                topics: this._topics,
                seq: this._curSeq
            }, this._abortCtrl.signal)
            if (q.length === 0) {
                continue
            }
            this._queue = q
            this._queueIdx = 0
            return this._GetNextFromQueue()
        }
    }

    /** Get as much as possible new events.
     * @return {Promise<{seq, topic, event}>[]}
     */
    async GetNextMultiple() {
        if (this._queue != null) {
            for (let i = this._queueIdx; i < this._queue.length; i++) {
                const e = this._queue[i]
                if (e.seq >= this._curSeq) {
                    this._curSeq = e.seq + 1
                }
            }
            const q = this._queue
            this._queue = null
            if (this._queueIdx === 0) {
                return q
            }
            return q.slice(this._queueIdx)
        }
        while (true) {
            const q = await PostRequest(this._url + "/PollEvents", {
                topics: this._topics,
                seq: this._curSeq
            })
            if (q.length === 0) {
                continue
            }
            for (let e of q) {
                if (e.seq >= this._curSeq) {
                    this._curSeq = e.seq + 1
                }
            }
            return q
        }
    }

    _GetNextFromQueue() {
        const e = this._queue[this._queueIdx]
        this._queueIdx++
        if (this._queueIdx >= this._queue.length) {
            this._queue = null
        }
        if (e.seq >= this._curSeq) {
            this._curSeq = e.seq + 1
        }
        return e
    }
}