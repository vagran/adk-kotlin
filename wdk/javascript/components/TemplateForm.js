goog.provide("wdk.components.TemplateForm");

/* Example of data object:
 * {
 *  fields: {
 *      someField: {
 *          label: "Some field",
 *          value: 42,
 *          type: "number", // "string", "check", "radio",
 *          placeholder: "Input value",
 *          disabled: true
 *      }
 *  }
 * }
 */
(function(app) {

    // language=HTML
    let tpl = `
<form @submit.prevent="_OnSubmit" :id="id">
    <table style="border-collapse: separate; border-spacing: 4px 2px; width: 100%;">
        <tbody>
        <tr v-for="(field, fieldName) in data.fields">
            <td><span :class="labelSizeClass">{{field.type !== "check" ? field.label : " "}}</span></td>
            <td>
                <input v-if="!field.hasOwnProperty('type') || field.type === 'string'" type="text" 
                       class="form-control" :class="controlSizeClass" v-model.trim="field.value"
                       :placeholder="field.placehoder !== undefined ? field.placeholder : null" 
                       :disabled="field.hasOwnProperty('disabled') && field.disabled" />
                <input v-else-if="field.type === 'number'" type="text" class="form-control"
                       :class="controlSizeClass" v-model.number="field.value"
                       :placeholder="field.placehoder !== undefined ? field.placeholder : null"
                       :disabled="field.hasOwnProperty('disabled') && field.disabled"/>
                <div v-else-if="field.type === 'check'" class="form-check">
                    <input type="checkbox" v-model="field.value" class="form-check-input"
                           :class="controlSizeClass" :id="id + '_' + fieldName"
                           :disabled="field.hasOwnProperty('disabled') && field.disabled"/>
                    <label class="form-check-label" :class="labelSizeClass"
                           :for="id + '_' + fieldName">{{field.label}}</label>
                </div>
                <!-- radio not yet implemented -->
                <div v-else style="color: #d00;">Unrecognized field type {{field.type}}</div>
            </td>
        </tr>
        </tbody>
    </table>
</form>
`;

    Vue.component("template-form", {
        template: tpl,

        props: {
            id: {
                required: true,
                type: String
            },
            data: {
                required: true
            },
            /** 0 - small, 1 - normal, 2 - large */
            size: {
                default: 1
            }
        },

        computed: {
            controlSizeClass() {
                switch (this.size) {
                case 0:
                    return {"form-control-sm": 1};
                case 1:
                    return {};
                case 2:
                    return {"form-control-lg": 1};
                case 3:
                    console.warn("Unrecognized size value: " + this.size);
                    return {};
                }
            },

            labelSizeClass() {
                switch (this.size) {
                    case 0:
                        return {"col-form-label-sm": 1};
                    case 1:
                        return {};
                    case 2:
                        return {"col-form-label-lg": 1};
                    case 3:
                        console.warn("Unrecognized size value: " + this.size);
                        return {};
                }
            }
        },

        methods: {
            _OnSubmit() {
                this.$emit("submit", this.data);
            }
        }
    });

})(window.app || (window.app = {}));
