goog.provide("wdk.components.EditableField");

(function(wdk) {

    // language=HTML
    let tpl = `
<div class="wdk_EditableField" @click="OnClick">
    <span v-if="!editing" class="label">{{value}} <i class="editButton fas fa-edit"></i></span>
    <form v-else class="input-group" @submit.prevent="OnEdited">
        <input type="text" class="form-control" v-model="editedValue" />
        <div class="input-group-append">
            <button class="btn btn-outline-danger" type="button" @click="OnCancel"><i class="fas fa-times"></i></button>
            <button class="btn btn-outline-success" type="submit"><i class="fas fa-check"></i></button>
        </div>
    </form>
</div>
`;
    Vue.component("editable-field", {
        template: tpl,

        props: {
            value: null
        },

        data() {
            return {
                editing: false,
                editedValue: null
            }
        },

        methods: {
            OnClick() {
                if (this.editing) {
                    return;
                }
                this.editedValue = this.value;
                this.editing = true;
            },

            OnCancel() {
                console.log("canceled");//XXX
                this.editing = false;
            },

            OnEdited() {
                //XXX emit event
                console.log(this.editedValue);//XXX
                this.editing = false;
            }
        }
    });

})(window.wdk || (window.wdk = {}));
