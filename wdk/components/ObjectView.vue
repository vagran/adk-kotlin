<!--
  - This file is part of ADK project.
  - Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
  - See LICENSE file for full license details.
  -->

<!-- Dumps arbitrary Javascript object. Mostly for debug purposes. -->

<template>
<div :class="{'container': true, 'containerRoot': isRoot}">
    <div v-if="title !== null" class="title">{{title}}</div>
    <div :class="valueTagClass">
        <span class="typeLabelOuter"><span :class="typeLabelClass">{{typeLabel}}</span></span>
        <span v-if="name !== null" class="name">{{name}}:</span>
        <span v-if="hasSingleValue" class="singleValue">{{singleValue}}</span>
        <span v-if="!hasSingleValue" class="elementsCount" @click="OnExpandToggle">({{elementsCount}} elements)</span>
        <div v-if="!hasSingleValue && isExpandedCur && elementsCount > 0" class="collectionValues" >
            <WdkObjectView v-for="(value, key) in object" :object="value" :isRoot="false"
                           :name="key" :key="key" :isExpanded="isExpanded"></WdkObjectView>
        </div>
    </div>
</div>
</template>

<script>

export default {
    name: "WdkObjectView",

    props: {
        object: {
            required: true
        },
        title: {
            default: null
        },
        isRoot: {
            default: true
        },
        name: {
            default: null
        },
        isExpanded: {
            default: true
        }
    },

    data() {
        return {
            someVar: [],
            isExpandedCur: this.isExpanded
        }
    },

    computed: {
        /**
         * @return {boolean} True if single value can be rendered.
         */
        hasSingleValue() {
            if (this.object === undefined) {
                return true
            }
            let type = this.GetType(this.object)
            return type === "null" || type === "number" || type === "boolean" ||
                type === "string" || type === "function"
        },

        valueTagClass() {
            let classes = {value: true}
            classes["value_" + this.GetType(this.object)] = true
            return classes
        },

        /**
         * @return {string} Content text for type label.
         */
        typeLabel() {
            if (this.object === undefined) {
                return "U"
            }
            let type = this.GetType(this.object)
            switch (type) {
                case "null":
                    /* Non-breaking space. */
                    return "\u00a0\u00a0\u00a0"
                case "number":
                    return "123"
                case "string":
                    return "ABC"
                case "boolean":
                    /* Checkbox symbol. */
                    return this.object ? "\u2611" : "\u2610"
                case "array":
                    return "[]"
                case "object":
                    return "{}"
                case "function":
                    return "F"
            }
            return "?"
        },

        typeLabelClass() {
            let classes = {"typeLabel": true}
            classes["typeLabel_" + this.GetType(this.object)] = true
            return classes
        },

        /**
         * @return {number} Number of child elements for collection type.
         */
        elementsCount() {
            return Object.keys(this.object).length
        },

        /**
         * @return {string} String representation of single value.
         */
        singleValue() {
            if (this.object === undefined) {
                return "undefined"
            }
            let type = this.GetType(this.object)
            if (type === "string") {
                return '"' + this.object + '"'
            } else if (type === "function") {
                return this.object.name
            } else if (type === "null") {
                return "null"
            }
            return this.object
        },
    },

    methods: {
        /**
         * @return {string} Name for type of the specified object.
         */
        GetType(obj) {
            if (obj === undefined) {
                return "undefined"
            }
            let typeStr = typeof obj
            if (this.object === null) {
                return "null"
            } else if (typeStr === "number" || typeStr === "boolean" || typeStr === "string" ||
                typeStr === "function") {
                return typeStr
            } else if (typeStr === "object") {
                if (Array.isArray(this.object)) {
                    return "array"
                } else {
                    return "object"
                }
            }
            return "unknown"
        },

        OnExpandToggle() {
            this.isExpandedCur = !this.isExpandedCur
        },
    }
}

</script>

<style scoped lang="less">

.container {
    font-family: monospace;
    font-size: 0.9rem;
    padding: 1px;

    .title {
        margin-left: 2em;
        margin-top: 0.2em;
        margin-bottom: 0.4em;
        font-family: sans-serif;
    }

    .typeLabelOuter {
        display: inline-block;
        width: 26px;
        vertical-align: middle;
        margin-right: 4px;
    }

    .typeLabel {
        display: inline-block;
        font-family: monospace;
        opacity: 0.6;
        font-size: small;
        border: 1px solid #b6b6b6;
        border-radius: 3px;
        background: #b3f6cc;
        padding: 1px 2px;
        float: right;
        margin-bottom: 2px;
    }

    .typeLabel_array, .typeLabel_object {
        padding: 0 3px 3px;
    }

    .typeLabel_null {
        padding-left: 0;
        padding-right: 0;
    }

    .typeLabel_boolean {
        font-size: medium;
        font-weight: bold;
        padding: 0 4px;
    }

    .typeLabel_undefined {
        font-size: medium;
        padding: 0 4px;
    }

    .value_array, .value_object {
        border: 1px solid #dddddd;
        border-radius: 2px;
    }

    .collectionValues {
        border-top: 1px dashed #e0e0e0;
    }

    .name {
        font-style: italic;
        opacity: 0.6;
    }

    .elementsCount {
        font-family: sans-serif;
        font-style: italic;
        font-size: small;
        opacity: 0.6;
        text-decoration: underline;
        cursor: pointer;
    }
}

.containerRoot {
    border: 1px solid #c2c2c2;
    border-radius: 2px;
    padding: 2px;
}

</style>
