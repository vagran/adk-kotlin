/* Stub file for including common stuff. */

goog.provide("wdk.common");

goog.require("wdk.error");
goog.require("wdk.class");

(function(wdk) {

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

})(window.wdk || (window.wdk = {}));
