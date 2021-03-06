/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

/* Stub file for including common stuff. */

goog.provide("wdk.common");

goog.require("wdk.error");
goog.require("wdk.class");

(function(wdk) {

    /* Refresh interval in ms for polled views. */
    wdk.REFRESH_INTERVAL = 1000;

    /** Parse URI query parameters into JS object.
     * @param query Optional query string. Current location is used if not specified.
     */
    wdk.GetQueryParams = function (query) {
        if (query === undefined) {
            query = location.search;
        }
        if (query.length === 0) {
            return {};
        }
        if (query[0] === '?') {
            query = query.substring(1);
        }
        let pairStrings = query.split('&');
        let result = {};
        for (let pairStr of pairStrings) {
            if (pairStr.length === 0) {
                continue;
            }
            let pair = pairStr.split('=');
            let name = decodeURIComponent(pair[0]);
            let value;
            if (pair.length > 1) {
                value = decodeURIComponent(pair[1]);
            } else {
                value = true;
            }
            if (name in result) {
                let prevValue = result[name];
                if (Array.isArray(prevValue)) {
                    prevValue.push(value);
                } else {
                    result[name] = [prevValue, value];
                }
            } else {
                result[name] = value;
            }
        }
        return result;
    };

    wdk.EscapeHtml = function (s) {
        return s
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    };

    wdk.PostRequest = function(url, data) {
        let params = {
            url: url,
            type: data !== undefined ? "POST" : "GET"
        };
        if (data !== undefined) {
            params.data = JSON.stringify(data);
            params.contentType = "application/json; charset=utf-8";
            params.dataType = "json";
        }
        return $.ajax(params);
    };

})(window.wdk || (window.wdk = {}));
