goog.provide("wdk.components.EditableField");

/** Emits "updated" event with a new value argument when edited. */
(function(wdk) {

    // language=HTML
    let tpl = `
<div class="wdk_EditableField" @click="_OnClick">
    <template v-if="!editing">
        <span v-if="!isLink" class="label">{{value}} <i class="editButton fas fa-edit"></i></span>
        <span v-else ><a :href="value" @click.stop rel="noreferrer">{{value}}</a> <i class="editButton fas fa-edit"></i></span>
    </template>
    <form v-else class="input-group" @submit.prevent="_OnEdited">
        <input ref="input" type="text" class="form-control form-control-sm" v-model="editedValue" @keydown.esc.stop="_OnCancel"/>
        <div class="input-group-append">
            <button class="btn btn-sm btn-outline-danger" type="button" @click.stop="_OnCancel"><i class="fas fa-times"></i></button>
            <button class="btn btn-sm btn-outline-success" type="submit"><i class="fas fa-check"></i></button>
        </div>
    </form>

    <message-box ref="msgBox" />
</div>
`;
    Vue.component("editable-field", {
        template: tpl,

        props: {
            value: {
                default: null
            },
            isLink: {
                default: false
            },
            /* Accepts new name and may throw exception with error text. */
            validator: {
                type: Function,
                default: null
            }
        },

        data() {
            return {
                editing: false,
                editedValue: null
            }
        },

        methods: {
            _OnClick() {
                if (this.editing) {
                    return;
                }
                this.editedValue = this.value;
                this.editing = true;
                this.$nextTick(() => this.$refs.input.focus());
            },

            _OnCancel() {
                this.editing = false;
            },

            _OnEdited() {
                if (this.validator !== null) {
                    try {
                        this.validator(this.editedValue);
                    } catch (e) {
                        this.$refs.msgBox.Show("error", e.message);
                        return;
                    }
                }
                this.editing = false;
                // noinspection EqualityComparisonWithCoercionJS
                if (this.value != this.editedValue) {
                    this.$emit("updated", this.editedValue);
                }
            }
        }
    });

})(window.wdk || (window.wdk = {}));
