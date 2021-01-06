<!--
  - This file is part of ADK project.
  - Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
  - See LICENSE file for full license details.
  -->

<template>
    <q-page padding class="root">
        WDK example application
        <div class="number">{{i}}</div>
        <q-icon class="icon" name="alarm"/>
        <q-icon class="icon" name="alarm_off"/>
        <img :src="sampleImage" class="icon" />
        <p>Count: {{counter}}</p>
        <q-btn color="primary" label="Pages">
            <q-menu auto-close>
                <q-list style="min-width: 100px">
                    <q-item clickable to="/page1/42?param=aaa">
                        <q-item-section>Page 1</q-item-section>
                    </q-item>
                    <q-separator />
                    <q-item clickable to="/page2/43?param=bbb">
                        <q-item-section>Page 2</q-item-section>
                    </q-item>
                </q-list>
            </q-menu>
        </q-btn>
        <q-btn @click="increment">Increment</q-btn>
        <p>
            <router-link to="/page1/42?param=aaa">Page1</router-link><br/>
            <router-link to="/page2/43?param=bbb">Page2</router-link>
        </p>
        <p><SampleComponent /></p>
        <router-view />
    </q-page>
</template>

<script>
import sampleImage from "@/assets/images/sample.svg"
import {mapMutations, mapState} from "vuex"
import SampleComponent from "@/components/SampleComponent"

export default {
    name: "LandingPage",
    components: {SampleComponent},
    computed: {
        ...mapState("someModule", ["counter"])
    },

    data() {
        return {
            i: 42
        }
    },

    methods: {
        ...mapMutations("someModule", ["increment"])
    },

    created()
    {
        this.sampleImage = sampleImage
    }
}
</script>

<style scoped lang="less">

.root {
    color: brown;
    .number {
        color: darkblue;
    }

    .icon {
        width: 64px;
        height: 64px;
        font-size: 64px;
        vertical-align: middle;
    }
}

</style>
