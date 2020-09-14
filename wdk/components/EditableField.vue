<!--
  - This file is part of ADK project.
  - Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
  - See LICENSE file for full license details.
  -->

<template>
<div class="root" @click="_OnClick">
    <template v-if="!editing">
        <span v-if="!isLink" class="label">{{value}}
            <q-icon class="text-blue-grey editButton" name="edit" size="xs"/></span>
        <span v-else ><a :href="value" @click.stop rel="noreferrer">{{value}}</a>
            <q-icon class="text-blue-grey editButton" name="edit" size="xs"/></span>
    </template>
    <form v-else @submit.prevent="_OnEdited">
        <q-input ref="input" v-model="editedValue" :dense="true" @keydown.esc.stop="_OnCancel">
            <template v-slot:append>
                <q-btn type="button" @click.stop="_OnCancel" icon="cancel" color="negative"
                       padding="none"/>
                <q-btn type="submit" @click.stop="_OnEdited" icon="check" color="positive"
                       padding="none"/>
            </template>
        </q-input>
    </form>
    <WdkMessageBox ref="msgBox" />
</div>
</template>

<script>

import WdkMessageBox from "./MessageBox";

/** Emits "input" event with a new value argument when edited. */
export default {
    name: "WdkEditableField",
    components: {WdkMessageBox},
    props: {
        value: {
            default: null
        },
        isLink: {
            default: false
        },
        /* Accepts new value and may throw exception with error text. */
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
                    this.$refs.msgBox.Show("error", e.message, "Validation error");
                    return;
                }
            }
            this.editing = false;
            // noinspection EqualityComparisonWithCoercionJS
            if (this.value != this.editedValue) {
                this.$emit("input", this.editedValue);
            }
        }
    }
}

</script>

<style scoped lang="less">

.root {
    display: inline-block;
    cursor: pointer;

    .label {}

    .editButton {
        opacity: 0.3;
    }

    &:hover .editButton {
        opacity: 0.9;
    }
}

</style>
