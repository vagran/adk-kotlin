goog.provide("wdk.error");
goog.require("wdk.class");

(function(wdk) {
    /**
     * Base class for all exceptions.
     * @class WdkError
     */
    wdk.Error = wdk.Class({
        className: "WdkError",

        /**
         * @param msg Error message.
         * @param cause Optional cause exception.
         */
        constructor(msg, cause) {
            this.message = msg;
            let stack = new Error().stack;
            this.stack = stack === undefined ? null : stack;
            this.cause = cause === undefined ? null : cause;
        },

        /**
         * @method ToString
         * @return {String} Error description with causes chain and stack traces if available.
         */
        ToString() {
            let clsName = this.className ? this.className :
                (this.constructor.name ? this.constructor.name : "{unknown class}");
            let result = clsName + ": " + this.message;
            if (this.stack) {
                result += "\n" + this.stack;
            }
            if (this.cause) {
                result += "\n\nCaused by\n" + this.cause.ToString();
            }
            return result;
        },

        toString() {
            return this.ToString();
        }
    });

    /**
     * For bug indications.
     * @class InternalError
     */
    wdk.InternalError = wdk.Class(wdk.Error, {
        className: "InternalError",

        constructor(msg, cause) {
            wdk.Error.call(this, msg, cause);
        }
    });

    /**
     * Indicates invalid parameter(s) value.
     */
    wdk.InvalidParamError = wdk.Class(wdk.Error, {
        className: "InvalidParamError",

        constructor(msg, cause) {
            wdk.Error.call(this, msg, cause);
        }
    });

    /**
     * Operation is not permitted in current state.
     */
    wdk.InvalidOpError = wdk.Class(wdk.Error, {
        className: "InvalidOpError",

        constructor(msg, cause) {
            wdk.Error.call(this, msg, cause);
        }
    });

})(window.wdk || (window.wdk = {}));
