/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

goog.provide("wdk.components.LocalId");

(function(wdk) {

    /**
     * @return {string} Short display string for the provided LocalId string.
     */
    wdk.LocalIdShortDisplay = function(id) {
        if (id === "0000000000000000") {
            return "0";
        }
        return ".." + id.substr(6, 2) + ".." + id.substr(14, 2);
    };

    // language=HTML
    let tpl = `
<span :title="value" class="wdk_LocalId">#{{display}}</span>
`;

    Vue.component("local-id", {
        template: tpl,
        props: {
            value: {
                type: String,
                required: true
            },
            full: {
                type: Boolean,
                default: false
            }
        },

        computed: {
            display() {
                if (this.full) {
                    return this.value;
                }
                return wdk.LocalIdShortDisplay(this.value);
            }
        }
    });

})(window.wdk || (window.wdk = {}));
