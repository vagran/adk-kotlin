goog.provide("wdk.components.StatusView_new");

(function(wdk) {

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
            status() {
                //XXX
            }
        },

        methods: {
            /**
             * Push some status message(s). The pushed messages are not tracked, so if the passed
             * object is changed after the call, it does not affect the displayed status.
             * @param status
             */
            Push(status) {

            }
        }
    });

})(window.wdk || (window.wdk = {}));
