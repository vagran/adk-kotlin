<!--
  - This file is part of ADK project.
  - Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
  - See LICENSE file for full license details.
  -->

<template>
<div class="WdkHtmlFilter">
    <template v-for="(node, idx) in nodes">{{idx > 0 ? " > " : ""}}
        <span class="node" :key="node.id" @click="_OnClick(node)">
            <template v-for="displayNode of _GetDisplayNodes(node)">
                <span v-if="typeof displayNode !== 'string'"
                      :class="_GetDisplayNodeClass(displayNode)">{{displayNode.text}}</span>
                <template v-else>{{displayNode}}</template>
            </template>
        </span>
    </template>
    <WdkHtmlFilter v-for="child in children" :data="child" :key="child.id" @nodeClicked="_OnClick"/>
</div>
</template>

<script>


import {MIN_INT} from "../../common/utils";

export default {
    name: "WdkHtmlFilter",
    
    props: {
        data: {
            default: null
        }
    },

    computed: {
        nodes() {
            let result = []
            let node = "root" in this.data ? this.data.root : this.data
            while (true) {
                result.push(node)
                let numChildren = node.children.length
                if (numChildren === 0 || numChildren > 1) {
                    break
                }
                node = node.children[0]
            }
            return result
        },

        children() {
            let node = "root" in this.data ? this.data.root : this.data
            while (true) {
                let numChildren = node.children.length
                if (numChildren === 0) {
                    return []
                }
                if (numChildren > 1) {
                    return node.children
                }
                node = node.children[0]
            }
        }
    },

    methods: {
        _GetDisplayNodes(node) {
            let result = []

            function AddText(text) {
                if (result.length !== 0 && typeof result[result.length] === "string") {
                    result[result.length] += text
                } else {
                    result.push(text)
                }
            }

            function AddSpan(cls, text) {
                result.push({cls: cls, text: text})
            }

            if (node.id === 0) {
                AddText("ROOT")

            } else if (node.isWildcard) {
                AddText("//")

            } else {
                if (node.elementName !== null) {
                    AddText(node.elementName)
                }
                if (node.htmlId !== null) {
                    AddText("#" + node.htmlId)
                }
                if (node.classes !== null) {
                    for (let cls of node.classes) {
                        AddText("." + cls)
                    }
                }
                if (node.attrs !== null) {
                    for (let attr of node.attrs) {
                        AddText("[" + attr.name)
                        if (attr.value !== null) {
                            AddText("=" + attr.value)
                        }
                        AddText("]")
                    }
                }
                if (node.index !== MIN_INT) {
                    AddText(":" + (node.index >= 0 ? node.index + 1 : node.index))
                }
                if (node.indexOfType !== MIN_INT) {
                    AddText(":T" + (node.indexOfType >= 0 ? node.indexOfType + 1 : node.indexOfType))
                }

                if (result.length === 0) {
                    AddText("*")
                }
            }

            if (node.extractInfo !== null) {
                for (let ei of node.extractInfo) {
                    if (ei.attrName !== null) {
                        AddText("[@")
                        if (ei.markNode) {
                            AddText("^")
                        }
                        AddSpan("TagName", ei.tagName)
                        AddText(":" + ei.attrName + "]")
                    } else {
                        AddText("@")
                        if (ei.extractText) {
                            AddText("~")
                        }
                        if (ei.markNode) {
                            AddText("^")
                        }
                        AddSpan("TagName", ei.tagName)
                    }
                }
            }

            return result
        },

        _GetDisplayNodeClass(node) {
            let result = {}
            result[node.cls] = true
            return result
        },

        _OnClick(node) {
            this.$emit("nodeClicked", node)
        }
    }
}
</script>

<style scoped lang="less">

.WdkHtmlFilter {
    font-family: "Roboto Mono", monospace;
    font-size: 0.8rem;

    .WdkHtmlFilter {
        margin-left: 2em;
        border-left: #e0e0e0 solid 1px;
        padding-left: 1em;
    }

    .WdkHtmlFilter:nth-of-type(n+2) {
        border-top: #e0e0e0 solid 1px;
    }

    .node {
        cursor: pointer;

        &:hover {
            background-color: #e0e0e0;
        }

        .TagName {
            font-weight: 600;
            color: #aa6739;
        }
    }
}
</style>