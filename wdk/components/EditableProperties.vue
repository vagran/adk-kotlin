<!--
  - This file is part of ADK project.
  - Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
  - See LICENSE file for full license details.
  -->

<template>
<div class="wdkEditableProperties">
    <table>
        <tbody>
        <tr v-for="field in sortedFields">
            <td class="label">{{field.label}}:</td>
            <td>
                <template v-if="field.type !== 'check' && field.hasOwnProperty('disabled') && field.disabled && (!field.hasOwnProperty('isLink') || !field.isLink)">
                    {{data[field.name]}}
                </template>
                <a v-else-if="field.hasOwnProperty('isLink') && field.isLink && field.hasOwnProperty('disabled') && field.disabled"
                   :href="data[field.name]">{{data[field.name]}}</a>
                <WdkEditableField v-else-if="IsTextField(field)"
                                :value="data[field.name]"
                                :isLink="field.hasOwnProperty('isLink') && field.isLink"
                                @input="(value) => _OnUpdated(field, value)"/>

                <div v-else-if="field.type === 'check'" class="form-check">
                    <q-checkbox dense :value="data[field.name]"
                                @input="(value) => _OnUpdated(field, value)"
                                :disable="field.hasOwnProperty('disabled') && field.disabled"/>
                </div>
                <div v-else-if="field.type === 'option'" >
                    <q-select @input="(value) => _OnUpdated(field, value)" :value="data[field.name]"
                              :options="_GetOptionValues(field)"
                              :disable="field.hasOwnProperty('disabled') && field.disabled"
                              dense options-dense map-options emit-value/>
                </div>

                <!-- radio not yet implemented -->
                <div v-else style="color: #dd0000;">Unrecognized field type {{field.type}}
                </div>
            </td>
        </tr>
        </tbody>
    </table>
    <WdkMessageBox ref="msgBox"/>
</div>
</template>
<script>

/* Example of fields object:
 * {
 *      someField: {
 *          label: "Some field",
 *          type: "number", // "integer", "float", "string", "check", "radio", "option"
 *          disabled: true,
 *          order: 1
 *      },
 *      linkField: {
 *          isLink: true
 *      },
 *      optionField: {
 *          type: "option",
 *          // may be function as well, each element can be either string or object with "label",
 *          // "value" and "disable" attributes.
 *          optionValues: ["value1", "value2"]
 *      }
 * }
 * Events:
 *  - updated(fieldName, newValue) - emitted when some property updated.
 */

import WdkEditableField from "./EditableField";
import WdkMessageBox from "./MessageBox"

export default {
    name: "WdkEditableProperties",
    components: {WdkEditableField, WdkMessageBox},
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
            let fields = []
            for (let fieldName in this.data) {
                if (!this.data.hasOwnProperty(fieldName)) {
                    continue
                }
                if (this.fields === null || !this.fields.hasOwnProperty(fieldName)) {
                    fields.push({label: fieldName, name: fieldName})
                    continue
                }
                let field = this.fields[fieldName]
                if (!field.hasOwnProperty("name")) {
                    field.name = fieldName
                }
                fields.push(field)
            }
            fields.sort((f1, f2) => {
                if (f1.hasOwnProperty("order")) {
                    if (f2.hasOwnProperty("order")) {
                        let diff = f1.order - f2.order
                        if (diff === 0) {
                            return f1.name.localeCompare(f2.name)
                        }
                        return diff
                    }
                    return -1
                }
                if (f2.hasOwnProperty("order")) {
                    return 1
                }
                return f1.name.localeCompare(f2.name)
            })
            return fields
        }
    },

    methods: {
        /** @return {boolean} */
        IsTextField(field) {
            return !field.hasOwnProperty('type') ||
                field.type === 'string' ||
                field.type === 'number' ||
                field.type === 'integer' ||
                field.type === 'float'
        },

        _OnUpdated(field, value) {
            if (field.type === "number") {
                value = Number(value)
            } else if (field.type === "integer") {
                value = parseInt(value)
            } else if (field.type === "float") {
                value = parseFloat(value)
            }
            if (Number.isNaN(value)) {
                this.$refs.msgBox.Show("error", `Invalid number value for ${field.name}`)
                return
            }
            if (field.hasOwnProperty("validator") && field.validator !== null) {
                try {
                    field.validator(value)
                } catch (e) {
                    this.$refs.msgBox.Show("error", e.message)
                    return
                }
            }
            this.$emit("updated", field.name, value)
        },

        _GetOptionValues(field) {
            let result = []
            let rawValues
            if (field.optionValues instanceof Function) {
                rawValues = field.optionValues()
            } else {
                rawValues = field.optionValues
            }
            for (let rawValue of rawValues) {
                if (rawValue instanceof Object) {
                    result.push(rawValue)
                } else {
                    let value = "" + rawValue
                    result.push({value: value, label: value})
                }
            }
            return result
        }
    }
}
</script>

<style scoped lang="less">

.wdkEditableProperties {
    table {
        border-collapse: separate;
        border-spacing: 1em 0.1em;

        td.label {
            text-align: right;
        }
    }
}

</style>
