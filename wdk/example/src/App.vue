<!--
  - This file is part of ADK project.
  - Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
  - See LICENSE file for full license details.
  -->
<template>
    <div class="root">
        WDK example application
        <div class="number">{{i}}</div>
    </div>
</template>

<script>

export default {
    data() {
        return {
            i: 42
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
}

</style>
