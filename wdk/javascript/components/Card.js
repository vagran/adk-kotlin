goog.provide("wdk.components.Card");

(function(app) {

    // language=HTML
    let tpl = `
<div class="card">
    <div v-if="header !== null" class="card-header" :class="headerClass">{{header}}</div>
    <div v-if="$slots.header" class="card-header" :class="headerClass"><slot name="header"></slot></div>
    <div class="card-body">
        <slot></slot>
    </div>
</div>`;

    Vue.component("card", {
        template: tpl,

        props: {
            /* No header if null. */
            header: {
                default: null
            },
            /* Decrease header height. */
            lowHeader: {
                default: false
            }
        },

        computed: {
            headerClass() {
                return {
                    "px-2": this.lowHeader,
                    "py-1": this.lowHeader
                };
            }
        }
    });

})(window.app || (window.app = {}));
