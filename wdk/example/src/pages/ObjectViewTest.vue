<!--
  - This file is part of ADK project.
  - Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
  - See LICENSE file for full license details.
  -->

<template>
<q-page padding>
    <q-card>
        <q-card-section>
            <WdkObjectView :object="emptyObject" class="q-my-sm"/>
            <WdkObjectView :object="sampleData" class="q-my-sm" />
            <WdkObjectView :object="sampleData" name="Sample name" class="q-my-sm"/>
        </q-card-section>
    </q-card>
</q-page>
</template>

<script>

export default {

    data() {
        return {
            emptyObject: {},
            sampleData: {
                a: 42,
                b: "aaa",
                c: null,
                d: [1, 2, 3],
                e: {
                    a: {},
                    b: undefined,
                    c: [],
                    d: true,
                    e: false
                }
            }
        }
    }
}

</script>
