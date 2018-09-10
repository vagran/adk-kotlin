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
        let pairStr = query.split('&');

        console.log(pairStr);//XXX
        return {aaa: 42};//XXX
    };

})(window.wdk || (window.wdk = {}));
