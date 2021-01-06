<!--
  - This file is part of ADK project.
  - Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
  - See LICENSE file for full license details.
  -->
<template>
<q-page padding>
    <q-card>
        <q-card-section>
            <q-toggle v-model="matchedOnly" label="Matched only"/>
            <WdkEditableProperties :fields="fields" :data="data" :matchedOnly="matchedOnly"
                                   @updated="OnUpdated"/>
            <WdkObjectView :object="data" class="q-ma-md"/>
            <h5>Array fields</h5>
            <WdkEditableProperties :fields="arrayFields" :data="data" :matchedOnly="matchedOnly"
                                   @updated="OnUpdated"/>
        </q-card-section>
    </q-card>
</q-page>
</template>

<script>

export default {

    data() {
        return {
            matchedOnly: false,
            fields: {
                number: {
                    label: "Number",
                    type: "number",
                    order: 1
                },
                integer: {
                    label: "Integer",
                    type: "integer",
                    order: 2
                },
                float: {
                    label: "Float",
                    type: "float",
                    order: 3
                },
                check: {
                    label: "Check",
                    type: "check",
                    order: 4
                },
                string: {
                    label: "String",
                    type: "string",
                    order: 5
                },

                disabled: {
                    label: "Disabled",
                    type: "number",
                    disabled: true,
                    order: 6
                },

                disabledTrue: {
                    label: "Disabled true",
                    type: "check",
                    disabled: true,
                    order: 7
                },
                disabledFalse: {
                    label: "Disabled false",
                    type: "check",
                    disabled: true,
                    order: 8
                },

                link: {
                    label: "Link",
                    isLink: true,
                    order: 9
                },

                disabledLink: {
                    label: "Disabled link",
                    isLink: true,
                    disabled: true,
                    order: 10
                },

                option: {
                    label: "Option",
                    type: "option",
                    optionValues: ["OPTION_1", "OPTION_2", "OPTION_3"],
                    order: 11
                },

                disabledOption: {
                    label: "Disabled option",
                    type: "option",
                    disabled: true,
                    optionValues: ["OPTION_1", "OPTION_2", "OPTION_3"],
                    order: 12
                },

                complexOption: {
                    label: "Complex option",
                    type: "option",
                    optionValues: [
                        {
                            value: "OPTION_1",
                            label: "Option 1"
                        },
                        {
                            value: "OPTION_2",
                            label: "Option 2",
                            disable: true
                        },
                        {
                            value: "OPTION_3",
                            label: "Option 3"
                        },
                    ],
                    order: 13
                },

                defaultField: {
                    default: 42,
                },

                hiddenField: {
                    hidden: true
                }
            },

            arrayFields: [{
                name: "number",
                label: "Number",
                type: "number"
            }, {
                name: "integer",
                label: "Integer",
                type: "integer"
            }, {
                name: "float",
                label: "Float",
                type: "float"
            }, {
                name: "check",
                label: "Check",
                type: "check"
            }, {
                name: "string",
                label: "String",
                type: "string"
            }, {
                name: "defaultField",
                default: 42
            }, {
                name: "hiddenField",
                hidden: true
            }],

            data: {
                number: 42,
                integer: 43,
                float: 44,
                check: false,
                string: "aaa",
                disabled: 45,
                disabledTrue: true,
                disabledFalse: false,
                link: "http://google.com",
                disabledLink: "http://example.com",
                option: "OPTION_1",
                disabledOption: "OPTION_2",
                complexOption: "OPTION_3",
                noFieldValue: 100,
                hiddenField: "this is hidden"
            }
        }
    },

    methods: {
        OnUpdated(fieldName, newValue) {
            console.log(fieldName, newValue)
            this.$set(this.data, fieldName, newValue)
        }
    }
}

</script>
