goog.provide("wdk.error");
goog.require("wdk.base");

/**
 * Base class for all exceptions.
 * @class WdkError
 */
let WdkError = Class({
    className: "WdkError",

    /**
     * @constructor
     * @param msg Error message.
     * @param cause Optional cause exception.
     */
    constructor: function(msg, cause) {
        this.message = msg;
        let stack = new Error().stack;
        this.stack = stack === undefined ? null : stack;
        this.cause = cause === undefined ? null : cause;
    },

    /**
     * @method ToString
     * @return {String} Error description with causes chain and stack traces if available.
     */
    ToString: function() {
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

    toString: function() {
        return this.ToString();
    }
});

/**
 * For bug indications.
 * @class InternalError
 */
let InternalError = Class(WdkError, {
    className: "InternalError",

    constructor: function(msg, cause) {
        WdkError.call(this, msg, cause);
    }
});

/**
 * Indicates invalid parameter(s) value.
 */
let InvalidParamError = Class(WdkError, {
    className: "InvalidParamError",

    constructor: function(msg, cause) {
        WdkError.call(this, msg, cause);
    }
});

/**
 * Operation is not permitted in current state.
 */
let InvalidOpError = Class(WdkError, {
    className: "InvalidOpError",

    constructor: function(msg, cause) {
        WdkError.call(this, msg, cause);
    }
});
