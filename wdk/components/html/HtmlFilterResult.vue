<!--
  - This file is part of ADK project.
  - Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
  - See LICENSE file for full license details.
  -->

<template>
<q-card class="WdkHtmlFilterResult">
    <div v-if="data === null" class="q-pa-sm">No data</div>
    <table v-else class="table table-sm q-mb-none">
        <tbody>
        <tr v-for="(nodes, tag) in data.nodes" :key="tag">
            <td style="width: 200px;"><span class="TagName">{{tag}}</span> [{{nodes.length}}]</td>
            <td>
                <table class="table table-sm table-bordered q-mb-none">
                    <tbody>
                    <tr v-for="node in nodes">
                        <td class="q-pl-sm"
                            style="max-width: 1000px; word-wrap: break-word;">
                        <span v-if="node.hasOwnProperty('text') && node.text !== null">{{node.text}}</span>
                        <wdk-html-viewer v-else-if="node.hasOwnProperty('docNode') && node.docNode !== null"
                                         :data="node.docNode" :uriResolver="uriResolver" />
                        <span v-else class="text-negative">Bad node</span>
                    </td></tr>
                    </tbody>
                </table>
            </td>
        </tr>
        </tbody>
    </table>
</q-card>
</template>

<script>

export default {
    name: "WdkHtmlFilterResult",

    props: {
        data: {
            default: null
        },

        uriResolver: {
            default: null
        }
    }
}

</script>

<style scoped lang="less">
.WdkHtmlFilterResult {

    .TagName {
        font-weight: 600;
        color: #aa6739;
    }

}
</style>