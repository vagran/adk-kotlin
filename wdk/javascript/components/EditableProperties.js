goog.provide("wdk.components.EditableProperties");

goog.require("wdk.components.EditableField");
goog.require("wdk.components.MessageBox");

/* Example of fields object:
 * {
 *      someField: {
 *          label: "Some field",
 *          type: "number", // "string", "check", "radio",
 *          disabled: true,
 *          order: 1
 *      }
 * }
 */
(function(app) {

    // language=HTML
    let tpl = `
<div class="EditableProperties">
    <table >
        <tbody>
        <tr v-for="field in sortedFields">
            <td class="label">{{field.label}}:</td>
            <td>
                <template v-if="field.hasOwnProperty('disabled') && field.disabled">
                    {{data[field.name]}}
                </template>
                <editable-field v-else-if="IsTextField(field)"
                                :value="data[field.name]"
                                :isLink="field.hasOwnProperty('isLink') && field.isLink"
                                @updated="(value) => _OnUpdated(field, value)"/>
                
                <div v-else-if="field.type === 'check'" class="form-check">
                    <input type="checkbox"  class="form-check-input position-static"
                           @change="(e) => _OnUpdated(field, e.target.checked)"
                           :disabled="field.hasOwnProperty('disabled') && field.disabled"/>
                </div>
                
                <!-- radio not yet implemented -->
                <div v-else style="color: #d00;">Unrecognized field type {{field.type}}</div>
            </td>
        </tr>
        </tbody>
    </table>
    <message-box ref="msgBox" />
</div>
`;

    Vue.component("editable-properties", {
        template: tpl,

        props: {
            fields: {
                default: null
            },
            data: {
                required: true
            }
        },

        computed: {
            sortedFields() {
                let fields = [];
                if (this.fields === null) {
                    for (let fieldName in this.data) {
                        if (!this.data.hasOwnProperty(fieldName)) {
                            continue;
                        }
                        fields.push({label: fieldName, name: fieldName})
                    }
                } else {
                    for (let fieldName in this.fields) {
                        if (!this.fields.hasOwnProperty(fieldName)) {
                            continue;
                        }
                        let field = this.fields[fieldName];
                        if (!field.hasOwnProperty("name")) {
                            field.name = fieldName;
                        }
                        fields.push(field);
                    }
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
            /** @return {boolean} */
            IsTextField(field) {
                return !field.hasOwnProperty('type') ||
                    field.type === 'string' ||
                    field.type === 'number' ||
                    field.type === 'integer' ||
                    field.type === 'float';
            },

            _OnUpdated(field, value) {
                if (field.type === "number") {
                    value = Number(value);
                } else if (field.type === "integer") {
                    value = parseInt(value);
                } else if (field.type === "float") {
                    value = parseFloat(value);
                }
                if (Number.isNaN(value)) {
                    this.$refs.msgBox.Show("error", `Invalid number value for ${field.name}`);
                    return;
                }
                if (field.hasOwnProperty("validator") && field.validator !== null) {
                    try {
                        field.validator(value);
                    } catch (e) {
                        this.$refs.msgBox.Show("error", e.message);
                        return;
                    }
                }
                this.$emit("updated", field.name, value);
            }
        }
    });

})(window.app || (window.app = {}));
