/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

goog.provide("wdk.class");
goog.require("wdk.base");


(function(wdk) {

    /**
     * Define class. The argument is prototype object. It should have "constructor" property with
     * the constructor definition. Base class also can be specified as the first argument.
     * Call base class constructor as "BaseClass.call(this, args)".
     * @module Class
     * @method Class
     */
    wdk.Class = function() {
        let proto;
        if (arguments.length === 2) {
            proto = Object.create(arguments[0].prototype);
            let clsProto = arguments[1];
            for (let key in clsProto) {
                if (clsProto.hasOwnProperty(key)) {
                    proto[key] = clsProto[key];
                }
            }
        } else if (arguments.length === 1) {
            proto = arguments[0];
        } else {
            throw "Unexpected number of arguments";
        }
        // Populate static members.
        if ("static" in proto) {
            for (let key in proto.static) {
                if (proto.static.hasOwnProperty(key)) {
                    proto.constructor[key] = proto.static[key];
                }
            }
            delete proto.static;
        }
        // Default class name
        if (!("className" in proto)) {
            proto.className = "{unnamed class}";
        }
        proto.constructor.prototype = proto;
        return proto.constructor;
    };

})(window.wdk || (window.wdk = {}));
