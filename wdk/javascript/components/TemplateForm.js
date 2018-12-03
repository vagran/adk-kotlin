goog.provide("wdk.components.TemplateForm");

/* Example of data object:
 * {
 *  fields: {
 *      someField: {
 *          label: "Some field",
 *          value: 42,
 *          type: "number", // "string", "check", "radio",
 *          placeholder: "Input value",
 *          disabled: true,
 *          order: 1
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
        <tr v-for="field in sortedFields">
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
                           :class="controlSizeClass" :id="id + '_' + field.name"
                           :disabled="field.hasOwnProperty('disabled') && field.disabled"/>
                    <label class="form-check-label" :class="labelSizeClass"
                           :for="id + '_' + field.name">{{field.label}}</label>
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
            },

            sortedFields() {
                let fields = [];
                for (let fieldName in this.data.fields) {
                    if (!this.data.fields.hasOwnProperty(fieldName)) {
                        continue;
                    }
                    let field = this.data.fields[fieldName];
                    if (!field.hasOwnProperty("name")) {
                        field.name = fieldName;
                    }
                    fields.push(field);
                }
                fields.sort((f1, f2) => {
                    if (f1.hasOwnProperty("order")) {
                        if (f2.hasOwnProperty("order")) {
                            let diff = f1.order - f2.order;
                            if (diff === 0) {
                                return f1.name.localeCompare(f2.name);
                            }
                            return diff;
                        }
                        return -1;
                    }
                    if (f2.hasOwnProperty("order")) {
                        return 1;
                    }
                    return f1.name.localeCompare(f2.name);
                });
                return fields;
            }
        },

        methods: {
            _OnSubmit() {
                this.$emit("submit", this.data);
            }
        }
    });

    app.TemplateForm = {
        CreateFields(desc, valueObj) {
            let fields = {};
            for (let fieldName in valueObj) {
                if (!valueObj.hasOwnProperty(fieldName) ||
                    !desc.hasOwnProperty(fieldName)) {
                    continue;
                }
                let field = Object.assign({}, desc[fieldName]);
                field.value = valueObj[fieldName];
                field.name = fieldName;
                fields[fieldName] = field;
            }
            return fields;
        },

        SetFields(desc, valueObj) {
            for (let fieldName in valueObj) {
                if (!valueObj.hasOwnProperty(fieldName) ||
                    !desc.hasOwnProperty(fieldName)) {
                    continue;
                }
                desc[fieldName].value = valueObj[fieldName];
                desc[fieldName].name = fieldName;
            }
        },

        GetFields(desc) {
            let result = {};
            for (let fieldName in desc) {
                if (!desc.hasOwnProperty(fieldName)) {
                    continue;
                }
                result[fieldName] = desc[fieldName].value;
            }
            return result;
        }
    };

})(window.app || (window.app = {}));
