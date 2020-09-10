<!--
  - This file is part of ADK project.
  - Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
  - See LICENSE file for full license details.
  -->
<template>
    <div class="root">
        WDK example application
        <div class="number">{{i}}</div>
        <fa-icon class="icon" icon="cat" />
        <fa-icon class="icon" :icon="['far', 'lemon']" />
        <img :src="sampleImage" style="width: 64px;"/>

        <div class="alert alert-success alert-dismissible fade show" role="alert">
            With Bootstrap!
            <button type="button" class="close" data-dismiss="alert" aria-label="Close">
                <span aria-hidden="true">&times;</span>
            </button>
        </div>
        <router-link to="/page1/42?param=aaa">Page1</router-link>
        <router-link to="/page2/43?param=bbb">Page2</router-link>
        <router-view />
    </div>
</template>

<script>
import sampleImage from "../assets/images/sample.svg";

export default {
    data() {
        return {
            i: 42,
            sampleImage
        }
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
    }
}

</style>
