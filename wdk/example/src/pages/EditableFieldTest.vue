<!--
  - This file is part of ADK project.
  - Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
  - See LICENSE file for full license details.
  -->
<template>
<q-page padding>
    <q-card>
        <q-card-section>
            Value [{{v1}}]: <WdkEditableField v-model="v1"/><br />
            Link [{{link}}: <WdkEditableField v-model="link" :isLink="true"/><br />
            Even value [{{even}}]: <WdkEditableField v-model="even" :validator="ValidateEven"/><br />
        </q-card-section>
    </q-card>
</q-page>
</template>

<script>

export default {

    data() {
        return {
            v1: 42,
            link: "http://google.com",
            even: 10
        }
    },

    methods: {
        ValidateEven(value) {
            if (value % 2 !== 0) {
                throw Error("Value should be even")
            }
        }
    }
}

</script>
