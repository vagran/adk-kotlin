/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

export async function PostRequest(url, data) {
    let params = {
        method: data !== undefined ? "POST" : "GET"
    }
    if (data !== undefined) {
        params.body = JSON.stringify(data)
        params.headers = {
            "Content-Type": "application/json; charset=utf-8"
        }
    }
    let response = await fetch(url, params)
    if (response.ok) {
        throw new Error(`Bad response: ${response.status} ${response.statusText}`)
    }
    return response.json()
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
