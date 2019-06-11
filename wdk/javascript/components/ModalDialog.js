goog.provide("wdk.components.ModalDialog");

(function(app) {

    // language=HTML
    let tpl = `
<div class="modal fade" ref="dialog" tabindex="-1">
    <div class="modal-dialog modal-dialog-centered" :style="dialogStyle">
        <div class="modal-content">
            <div class="modal-header">
                <h5 v-if="title !== null" class="modal-title">{{title}}</h5>
                <button v-if="hasCloseIconButton" type="button" class="close" data-dismiss="modal">
                    <span>&times;</span>
                </button>
            </div>
            <div class="modal-body">
                <slot />
            </div>
            <div class="modal-footer">
                <button v-if="hasCloseButton" type="button" class="btn btn-secondary" 
                        data-dismiss="modal">Close</button>
                <slot name="footer" />
            </div>
        </div>
    </div>
</div>
`;

    Vue.component("modal-dialog", {
        template: tpl,

        props: {
            title: {
                default: null
            },
            hasCloseButton: {
                default: false
            },
            hasCloseIconButton: {
                default: true
            },
            maxWidth: {
                type: String,
                default: null
            }
        },

        computed: {
            dialogStyle() {
                if (this.maxWidth === null) {
                    return {};
                } else {
                    return {"max-width": this.maxWidth};
                }
            }
        },

        methods: {
            Show() {
                $(this.$refs.dialog).modal("show");
            },

            Hide() {
                $(this.$refs.dialog).modal("hide");
            }
        }
    });

})(window.app || (window.app = {}));
