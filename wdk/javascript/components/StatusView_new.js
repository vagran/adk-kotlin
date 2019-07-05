goog.provide("wdk.components.StatusView_new");

(function (wdk) {

    // language=HTML
    let tpl = `
<div v-if="hasVisibleItems" class="wdk_StatusView">

    <!-- XXX expandable, styled badges for hidden count -->
</div>
    `;

    Vue.component("status-view-new", {
        template: tpl,
        props: {
            /* Can be either status object, or array, or aggregated object (primary types).
             *
             * Each item of an array can be any of the primary types.
             *
             * Aggregated object should have property "svAggregated: true". In such case all its
             * rest properties can be any of the primary types.
             *
             * Status object can be one of:
             *
             * * String. In such case it is interpreted as status message. The message can have
             *      a prefix for specifying level. Possible values:
             *    * "E>" - error
             *    * "W>" - warning
             *    * "I>" - info
             *    * "S>" - success
             *    * "%>" - progress (spinner is displayed)
             *    * "P>" - primary
             *    * "L>" - light
             *    * "D>" - dark
             *    * no prefix - secondary
             *
             * * Status item object:
             *   {
             *      text: <message text>,
             *      level: <optional message level, default is secondary>,
             *      details: <optional details text>,
             *      isHtml: <optional indication to interpret text as HTML, default is false>,
             *      title: <optional message title>
             *   }
             *
             * * jQuery XHR error.
             *
             * * jQuery XHR result with ADK domain server JSON error.
             *
             * * Status wrapper, specifies some common properties for embedded status object(s).
             *      The properties affect all the wrapped status objects, if they are not overridden
             *      in a particular object.
             *  {
             *      svWrapped: true,
             *      s: <wrapped status, any of the primary types>,
             *      isHtml: <optional indication to interpret text as HTML, default is false>,
             *      title: <optional message title>
             *  }
             */
            "status": {
                default: null
            },

            /* Expiration time for individual messages, seconds. Zero to disable expiration. */
            "expirationTimeout": {
                default: 120
            },

            /* Maximal number of items to display when not expanded. */
            "maxCollapsedCount": {
                default: 4
            },

            /* Display as floating toast. ? XXX*/
            "isToast": {
                default: false
            }
        },

        data() {
            return {
                /* {
                 *      id: <assigned ID>,
                 *      text: <text>,
                 *      level: <level>,
                 *      details: <details text, may be null>,
                 *      isHtml: <{boolean}>,
                 *      title: <title text, may be null>,
                 *      timestamp: <Date.getTime()>,
                 *      count: <number of aggregated messages>
                 * }
                 */
                items: [],

                isExpanded: false
            }
        },

        computed: {
            hasVisibleItems() {

                //XXX
                return true;
            },
        },

        watch: {
            status(status) {
                let items = this._ParseStatus(status);
                for (let item in items) {

                }
            }
        },

        mounted() {
            this.curId = 1;
        },

        methods: {
            /**
             * Push some status message(s). The pushed messages are not tracked, so if the passed
             * object is changed after the call, it does not affect the displayed status.
             * @param status
             */
            Push(status) {
                this._PushImpl(status, false);
                this._SortItems();
            },

            /**
             * @param status Any supported status object.
             * @param doMerge Do not increment aggregation counter and do not update timestamp if
             *      the same message found in current messages list.
             */
            _PushImpl(status, doMerge) {

            },

            _SortItems() {
                //XXX
            },

            /** Check it two items look equals. */
            _ItemEquals(item1, item2) {
                return item1.text === item2.text &&
                    item1.details !== item2.details &&
                    item1.isHtml === item2.isHtml &&
                    item1.title === item2.title;
            },

            _ParseStatus(status) {
                let result = [];
                let ctx = {
                    title: null,
                    isHtml: false,
                    timestamp: Date.getTime()
                };
                this._ParseStatusImpl(status, ctx, result);
                return result;
            },

            /** Parse status object of any supported type into list of status items.
             * @param status Any supported status object.
             * @param ctx Context object with shared properties.
             * @param result Array to append result to.
             */
            _ParseStatusImpl(status, ctx, result) {
                let type = $.type(status);

                if (type === "array") {
                    for (let index = 0; index < status.length; index++) {
                        let item = status[index];
                        this._ParseStatusImpl(item, ctx, result);
                    }

                } else if (type === "object") {
                    if (status.hasOwnProperty("svAggregated") && status.svAggregated === true) {
                        /* Aggregated status object. */
                        for (let propName in status) {
                            if (!status.hasOwnProperty(propName) || propName === "svAggregated") {
                                continue;
                            }
                            this._ParseStatusImpl(status[propName], ctx, result);
                        }

                    } else if (status.hasOwnProperty("svWrapped") && status.svWrapped === true) {
                        /* Wrapper object. */
                        let newCtx = Object.assign({}, ctx);
                        if (status.hasOwnProperty("isHtml")) {
                            newCtx.isHtml = status.isHtml;
                        }
                        if (status.hasOwnProperty("title")) {
                            newCtx.title = status.title;
                        }
                        this._ParseStatusImpl(status.s, newCtx, result);

                    } else {
                        let item = this._ParseStatusItem(status, ctx);
                        if (item !== null) {
                            result.push(item);
                        }
                    }

                } else {
                    let item = this._ParseStatusItem(status, ctx);
                    if (item !== null) {
                        result.push(item);
                    }
                }
            },

            /** Parse one of supported non-aggregated status object into status item. */
            _ParseStatusItem(status, ctx) {
                let result = {
                    details: null,
                    isHtml: ctx.isHtml,
                    title: ctx.title,
                    timestamp: ctx.timestamp
                };

                if ($.type(status) === "string") {
                    if (status.startsWith("I>")) {
                        result.level = "info";
                        result.text = status.substr(2);
                    } else if (status.startsWith("W>")) {
                        result.level = "warning";
                        result.text = status.substr(2);
                    } else if (status.startsWith("E>")) {
                        result.level = "error";
                        result.text = status.substr(2);
                    } else if (status.startsWith("P>")) {
                        result.level = "primary";
                        result.text = status.substr(2);
                    } else if (status.startsWith("S>")) {
                        result.level = "success";
                        result.text = status.substr(2);
                    } else if (status.startsWith("%>")) {
                        result.level = "progress";
                        result.text = status.substr(2);
                    } else if (status.startsWith("L>")) {
                        result.level = "light";
                        result.text = status.substr(2);
                    } else if (status.startsWith("D>")) {
                        result.level = "dark";
                        result.text = status.substr(2);
                    } else {
                        result.level = "secondary";
                        result.text = status;
                    }

                } else if (status.hasOwnProperty("responseJSON") && status.responseJSON !== null &&
                    status.responseJSON.hasOwnProperty("message")) {

                    /* JSON error from backend. */
                    result.level = "error";
                    result.text = status.responseJSON.message;
                    if (status.responseJSON.hasOwnProperty("fullText")) {
                        result.details = status.responseJSON.fullText;
                    }

                } else if (status.hasOwnProperty("status")) {
                    /* jQuery XHR error. */
                    result.level = "error";
                    result.text = status.status.toString();
                    if (status.hasOwnProperty("statusText")) {
                        result.text += " " + status.statusText;
                    }

                } else if (status.hasOwnProperty("text")) {
                    /* Generic status object. */
                    result.text = status.text;
                    result.isHtml = status.isHtml;
                    if (status.hasOwnProperty("details")) {
                        result.details = status.details;
                    }
                    if (status.hasOwnProperty("level")) {
                        result.level = status.level;
                    } else {
                        result.alertClass = "alert-secondary";
                    }

                } else {
                    console.warn("Unrecognized status object", status);
                    return null;
                }

                result.id = this.curId++;
                result.count = 1;

                return result;
            },

            _GetAlertClass(level) {
                if (level === "info") {
                    return "alert-info";
                } else if (level === "warn" || level === "warning") {
                    return "alert-warning";
                } else if (level === "error" || level === "danger") {
                    return "alert-danger";
                } else if (level === "primary") {
                    return "alert-primary";
                } else if (level === "success") {
                    return "alert-success";
                } else if (level === "secondary" || level === "progress") {
                    return "alert-secondary";
                } else if (level === "light") {
                    return "alert-light";
                } else if (level === "dark") {
                    return "alert-dark";
                } else {
                    console.warn("Unrecognized alert class", level);
                    return "alert-secondary";
                }
            }
        }
    });

})(window.wdk || (window.wdk = {}));
