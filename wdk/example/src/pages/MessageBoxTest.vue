<!--
  - This file is part of ADK project.
  - Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
  - See LICENSE file for full license details.
  -->

<template>
<q-page padding>
    <q-card>
        <q-card-section>
            <q-btn label="error" @click="Show('error')"/>
            <q-btn label="warning" @click="Show('warning')"/>
            <q-btn label="info" @click="Show('info')"/>
            <q-btn label="success" @click="Show('success')"/>
            <WdkMessageBox ref="msgBox" />
        </q-card-section>
    </q-card>
</q-page>
</template>

<script>

export default {

    data() {
        return {
        }
    },

    computed: {

    },

    methods: {
        Show(type) {
            this.$refs.msgBox.Show(type, "Sample message lorem ipsum dolor sit", "Sample title")
        }
    }
}

</script>
