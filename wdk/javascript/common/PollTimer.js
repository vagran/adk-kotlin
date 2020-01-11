/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

goog.provide("wdk.PollTimer");

goog.require("wdk.error");
goog.require("wdk.class");

(function(wdk) {

    /** Parse URI query parameters into JS object.
     * @param query Optional query string. Current location is used if not specified.
     */
    wdk.PollTimer = wdk.Class({
        className: "PollTimer",

        /** @param pollInterval Polling interval in ms. */
        constructor(pollInterval) {
            this.pollInterval = pollInterval;
            this.timer = setInterval(() => this._OnTimer(), pollInterval);
            this.polls = {};
        },

        Stop() {
            if (this.timer !== null) {
                clearInterval(this.timer);
                this.timer = null;
            }
        },

        /** Register new poll.
         * @param name Name used for poll management.
         * @param requestProvider Function which should generate request and return deferred object
         * for result. It can return null to stop polling.
         * @param interval Interval to poll with. Can be omitted to poll with timer interval.
         */
        Poll(name, requestProvider, interval = null) {
            let existingPoll = this.polls[name];
            if (existingPoll && existingPoll.isInProgress) {
                existingPoll.requestProvider = requestProvider;
                existingPoll.interval = interval;
                return;
            }
            let poll = {
                requestProvider: requestProvider,
                interval: interval,
                isInProgress: false,
                lastRequestTime: 0,
                deletePending: false
            };
            this.polls[name] = poll;
            this._SendRequest(poll);
        },

        Cancel(name) {
            let poll = this.polls[name];
            if (poll) {
                poll.deletePending = true;
            }
        },

        _OnTimer() {
            let deleteList = [];
            let now = new Date().getTime();
            for (let pollName in this.polls) {
                let poll = this.polls[pollName];
                if (poll.deletePending) {
                    deleteList.push(pollName);
                    continue;
                }
                if (poll.isInProgress) {
                    continue;
                }
                if (poll.interval === null || poll.lastRequestTime + poll.interval <= now) {
                    this._SendRequest(poll, now);
                }
            }
            for (let pollName of deleteList) {
                delete this.polls[pollName];
            }
        },

        _SendRequest(poll, now = null) {
            poll.isInProgress = true;
            poll.lastRequestTime = now !== null ? now : new Date().getTime();
            let def = poll.requestProvider();
            if (def == null) {
                poll.deletePending = true;
                return;
            }
            def.always(() => {
                poll.isInProgress = false;
                let now = new Date().getTime();
                let interval = poll.interval !== null ? poll.interval : this.pollInterval;
                if (poll.lastRequestTime + interval <= now) {
                    this._SendRequest(poll, now);
                }
            });
        }
    });

})(window.wdk || (window.wdk = {}));
