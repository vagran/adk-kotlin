/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

/* Default refresh interval in ms for polled views. */
export const REFRESH_INTERVAL = 1000

export const MIN_INT = -2147483648

export class PostRequestError extends Error {
    constructor(message, json) {
        super(message)
        if (json !== null) {
            this.responseJSON = json
        }
    }
}

/**
 * @param url
 * @param data Request body data (will be encoded in JSON). GET request if not specified, POST
 *  otherwise.
 * @param abortSignal {AbortSignal|null} Abort signal to connect the fetch to.
 * @return {Promise<null|any>} Decoded JSON response.
 */
export async function PostRequest(url, data = null, abortSignal = null) {
    let params = {
        method: data !== null ? "POST" : "GET"
    }
    if (data !== null) {
        params.body = JSON.stringify(data)
        params.headers = {
            "Content-Type": "application/json; charset=utf-8"
        }
    }
    if (abortSignal !== null) {
        params.signal = abortSignal
    }
    let response = await fetch(url, params)

    if (!response.ok) {
        let json = null
        let type = response.headers.get("Content-Type")
        if (type !== null && type.startsWith("application/json")) {
            try {
                json = await response.json()
            } catch {}
        }
        throw new PostRequestError(`Bad response: ${response.status} ${response.statusText}`, json)
    }

    if (response.status === 204) {
        return null
    }

    return await response.json()
}

export class PollTimer {
    /** @param pollInterval Polling interval in ms. */
    constructor(pollInterval) {
        this.pollInterval = pollInterval
        this.timer = setInterval(() => this._OnTimer(), pollInterval)
        this.polls = {}
    }

    Stop() {
        if (this.timer !== null) {
            clearInterval(this.timer)
            this.timer = null
        }
    }

    /** Register new poll.
     * @param name Name used for poll management.
     * @param requestProvider Function which should generate request and return deferred object
     * for result. It can return null to stop polling.
     * @param interval Interval to poll with. Can be omitted to poll with timer interval.
     */
    Poll(name, requestProvider, interval = null) {
        let existingPoll = this.polls[name]
        if (existingPoll && existingPoll.isInProgress) {
            existingPoll.requestProvider = requestProvider
            existingPoll.interval = interval
            return
        }
        let poll = {
            requestProvider: requestProvider,
            interval: interval,
            isInProgress: false,
            lastRequestTime: 0,
            deletePending: false
        }
        this.polls[name] = poll
        this._SendRequest(poll)
    }

    Cancel(name) {
        let poll = this.polls[name]
        if (poll) {
            poll.deletePending = true
        }
    }

    _OnTimer() {
        let deleteList = []
        let now = new Date().getTime()
        for (let pollName in this.polls) {
            let poll = this.polls[pollName]
            if (poll.deletePending) {
                deleteList.push(pollName)
                continue
            }
            if (poll.isInProgress) {
                continue
            }
            if (poll.interval === null || poll.lastRequestTime + poll.interval <= now) {
                this._SendRequest(poll, now)
            }
        }
        for (let pollName of deleteList) {
            delete this.polls[pollName]
        }
    }

    _SendRequest(poll, now = null) {
        poll.isInProgress = true
        poll.lastRequestTime = now !== null ? now : new Date().getTime()
        let def = poll.requestProvider()
        if (def == null) {
            poll.deletePending = true
            return
        }
        def.finally(() => {
            poll.isInProgress = false
            let now = new Date().getTime()
            let interval = poll.interval !== null ? poll.interval : this.pollInterval
            if (poll.lastRequestTime + interval <= now) {
                this._SendRequest(poll, now)
            }
        })
    }
}
