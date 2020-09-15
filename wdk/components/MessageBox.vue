<!--
  - This file is part of ADK project.
  - Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
  - See LICENSE file for full license details.
  -->

<template>
<q-dialog :value="isShown" @input="OnInput">
    <q-card>
        <q-bar :class="[`text-${textColor}`, `bg-${bgColor}`]">
            <q-icon v-if="icon !== null" :name="icon" size="sm"/>
            <q-space />
            <div v-if="title !== null" class="text-weight-bold">{{title}}</div>
            <q-space />
            <q-btn dense flat icon="close" v-close-popup>
                <q-tooltip>Close</q-tooltip>
            </q-btn>
        </q-bar>

        <q-card-section>
            {{message}}
        </q-card-section>
    </q-card>
</q-dialog>
</template>

<script>

export default {
    name: "WdkMessageBox",

    props: {
        value: {
            default: false
        }
    },

    computed: {
        icon() {
            switch (this.type) {
            case "info":
            case "warning":
            case "error":
                return this.type
            case "success":
                return "check_circle"

            default:
                return null
            }
        },

        textColor() {
            switch (this.type) {
                case "info":
                    return "cyan-10"
                case "warning":
                    return "deep-orange-10"
                case "error":
                    return "blue-grey-1"
                case "success":
                    return "blue-grey-1"

                default:
                    return "blue-grey-10"
            }
        },

        bgColor() {
            switch (this.type) {
                case "info":
                case "warning":
                    return this.type
                case "error":
                    return "negative"
                case "success":
                    return "positive"

                default:
                    return "blue-grey-2"
            }
        },
    },

    data() {
        return {
            isShown: false,
            type: "info",
            message: "",
            title: null
        }
    },

    methods: {
        OnInput(v) {
            this.isShown = v
            this.$emit("input", v)
        },

        /**
         * @param type One of "info", "success", "warning", "error"
         * @param message
         * @param title Optional title
         */
        Show(type, message, title) {
            this.type = type
            this.message = message
            this.title = title !== undefined ? title : null
            this.isShown = true
            this.$emit("input", true)
        }
    }
}

</script>
