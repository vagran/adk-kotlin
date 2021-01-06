/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

goog.provide("wdk.components.MessageBox");

(function(app) {

    // language=HTML
    let tpl = `
        <div class="modal fade" ref="dialog" tabindex="-1">
            <div class="modal-dialog modal-dialog-centered">
                <div class="modal-content" :style="contentStyles" >
                    <div class="modal-header" :class="headerClasses" :style="headerStyles">
                        <h5 class="modal-title" v-if="title !== null">{{title}}</h5>
                        <button type="button" class="close" data-dismiss="modal">
                            <span :class="headerTextClasses">&times;</span>
                        </button>
                    </div>
                    <div class="modal-body" :class="contentClasses">{{message}}</div>
                </div>
            </div>
        </div>
`;

    Vue.component("message-box", {
        template: tpl,

        data() {
            return {
                type: "info",
                message: "",
                title: null
            }
        },

        methods: {
            Show(type, message, title) {
                this.type = type;
                this.message = message;
                this.title = title !== undefined ? title : null;
                $(this.$refs.dialog).modal("show");
            }
        },

        computed: {
            contentClasses() {
                switch (this.type) {
                    case "info":
                        return {"alert-info": true};
                    case "success":
                        return {"alert-success": true};
                    case "warn":
                    case "warning":
                        return {"alert-warning": true};
                    case "error":
                        return {"alert-danger": true};
                }
                return {};
            },

            headerClasses() {
                switch (this.type) {
                    case "info":
                        return {"bg-info": true, "text-white": true};
                    case "success":
                        return {"bg-success": true, "text-white": true};
                    case "warn":
                    case "warning":
                        return {"bg-warning": true};
                    case "error":
                        return {"bg-danger": true, "text-white": true};
                }
                return {};
            },

            headerTextClasses() {
                switch (this.type) {
                    case "info":
                    case "success":
                    case "error":
                        return {"text-white": true};
                    case "warn":
                    case "warning":
                        return {"bg-warning": true};
                }
                return {};
            },

            headerStyles() {
                switch (this.type) {
                    case "info":
                    case "success":
                    case "error":
                    case "warn":
                    case "warning":
                        return {"border-bottom": "0"};
                }
                return {};
            },

            contentStyles() {
                switch (this.type) {
                    case "info":
                    case "success":
                    case "error":
                    case "warn":
                    case "warning":
                        return {"background-color": "inherit"};
                }
                return {};
            }
        }
    });

})(window.app || (window.app = {}));
