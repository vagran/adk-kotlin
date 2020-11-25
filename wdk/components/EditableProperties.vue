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
 *      hiddenField: {
 *          hidden: true
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
 * May be array as well, elements should have "name" element, "order" field is not used.
 * Events:
 *  - updated(fieldName, newValue) - emitted when some property updated.
 */

import WdkEditableField from "./EditableField";
import WdkMessageBox from "./MessageBox"

const Chars = {
    a: "a".codePointAt(0),
    z: "z".codePointAt(0),
    A: "A".codePointAt(0),
    Z: "Z".codePointAt(0),
    _: "_".codePointAt(0),

    isAlpha(c) {
        return (c >= Chars.A && c <= Chars.Z) || (c >= Chars.a && c <= Chars.z)
    },

    isUpper(c) {
        return c >= Chars.A && c <= Chars.Z
    },

    isLower(c) {
        return c >= Chars.a && c <= Chars.z
    }
}

export default {
    name: "WdkEditableProperties",
    components: {WdkEditableField, WdkMessageBox},
    props: {
        fields: {
            default: null
        },
        data: {
            required: true
        },
        /** Do not display fields which are not described in fields object. */
        matchedOnly: {
            default: false
        }
    },

    computed: {
        sortedFields() {
            let fields = []

            if (Array.isArray(this.fields)) {
                let fieldsSeen = new Set()
                for (let field of this.fields) {
                    if (!this.data.hasOwnProperty(field.name)) {
                        continue
                    }
                    if (!field.hasOwnProperty("label")) {
                        field.label = this._MakeLabel(field.name)
                    }
                    if (!field.hasOwnProperty("hidden") || !field.hidden) {
                        fields.push(field)
                    }
                    fieldsSeen.add(field.name)
                }
                if (!this.matchedOnly) {
                    let unlabeledFields = []
                    for (let fieldName in this.data) {
                        if (!this.data.hasOwnProperty(fieldName)) {
                            continue
                        }
                        if (!fieldsSeen.has(fieldName)) {
                            unlabeledFields.push({label: fieldName, name: fieldName})
                        }
                    }
                    unlabeledFields.sort((f1, f2) => {
                        return f1.name.localeCompare(f2.name)
                    })
                    fields.push(...unlabeledFields)
                }

            } else {
                for (let fieldName in this.data) {
                    if (!this.data.hasOwnProperty(fieldName)) {
                        continue
                    }
                    if (this.fields === null || !this.fields.hasOwnProperty(fieldName)) {
                        if (!this.matchedOnly) {
                            fields.push({label: fieldName, name: fieldName})
                        }
                        continue
                    }
                    let field = this.fields[fieldName]
                    if (!field.hasOwnProperty("name")) {
                        field.name = fieldName
                    }
                    if (!field.hasOwnProperty("label")) {
                        field.label = this._MakeLabel(fieldName)
                    }
                    if (!field.hasOwnProperty("hidden") || !field.hidden) {
                        fields.push(field)
                    }
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
            }

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
        },

        _MakeLabel(name) {
            let result = ""
            let word = ""
            let isAbbr = false
            let firstUpper = false

            function CommitWord() {
                if (word.length === 0) {
                    return
                }
                if (result.length !== 0) {
                    result += " "
                    result += word.charAt(0).toLowerCase()
                } else {
                    result += word.charAt(0).toUpperCase()
                }
                result += word.slice(1)
                word = ""
            }

            function ProcessChar(cS, c) {
                if (Chars.isAlpha(c)) {
                    if (word.length === 0) {
                        word += cS
                        firstUpper = Chars.isUpper(c)
                        isAbbr = false
                        return true
                    } else if (isAbbr) {
                        if (Chars.isLower(c)) {
                            CommitWord()
                            return false
                        }
                        word += cS
                        return true
                    } else if (word.length === 1) {
                        if (Chars.isUpper(c)) {
                            if (firstUpper) {
                                isAbbr = true
                            } else {
                                CommitWord()
                                return false
                            }
                        }
                        word += cS
                        return true
                    } else if (Chars.isUpper(c)) {
                        CommitWord()
                        return false
                    }
                    word += cS
                    return true

                } else {
                    CommitWord()
                    if (c === Chars._) {
                        result += " "
                    } else {
                        result += cS
                    }
                    return true
                }
            }

            for (const cS of name) {
                const c = cS.codePointAt(0)
                // noinspection StatementWithEmptyBodyJS
                while (!ProcessChar(cS, c));
            }
            CommitWord()
            return result.trim()
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
