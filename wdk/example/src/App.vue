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

        <div class="alert alert-success alert-dismissible fade show" role="alert">
            With Bootstrap!
            <button type="button" class="close" data-dismiss="alert" aria-label="Close">
                <span aria-hidden="true">&times;</span>
            </button>
        </div>
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

    .icon {
        width: 64px;
        height: 64px;
    }
}

</style>
