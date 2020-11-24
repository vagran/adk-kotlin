<!--
  - This file is part of ADK project.
  - Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
  - See LICENSE file for full license details.
  -->

<template>
<div class="WdkHtmlElement" v-if="element !== null">
    <table style="width: 100%" v-if="isText"><tbody>
    <tr>
        <td v-if="allowSel">
                <span class="selCheckbox px-1"
                      :class="{selected: element.isSelected}">
                <q-checkbox size="xs" dense v-model="element.isSelected" /></span>
        </td>
        <td style="width:100%">
            <div class="text" :class="{modeInserted: mode === Mode.INSERTED,
                                       modeDeleted: mode === Mode.DELETED}">{{element.text}}</div>
        </td>
    </tr>
    </tbody></table>
    <span class="expandButton" :class="{expanded: isExpanded, collapsed: !isExpanded}"
          v-if="isExpandable" @click="_ToggleExpand()"></span>
    <span class="name"
          :class="{collapsed: isExpandable && !isExpanded, expandable: isExpandable, modeInserted: mode == Mode.INSERTED, modeDeleted: mode == Mode.DELETED, changed: isChanged}"
          @click="_ToggleExpand()"
          v-if="!isText">&lt;{{isClosing ? "/" : ""}}{{name}}{{!isExpandable && isClosed && !isClosing ? " /" : ""}}&gt;{{isClosed ? "" : "..."}}</span>
    <span v-if="allowSel && !isText"
          class="selCheckbox px-1"
          :class="{selected: element.isSelected}"><q-checkbox size="xs" dense v-model="element.isSelected" /></span>
    <q-badge v-for="tag of tags" color="orange-9">{{tag}}</q-badge>
    <table class="table table-sm attributes" v-if="hasAttributes && isExpanded"><tbody>
    <tr v-for="attr in element.attrs" :key="attr.id"
        :class="{modeInserted: _GetMode(attr) === Mode.INSERTED,
                 modeDeleted: _GetMode(attr) === Mode.DELETED}">
        <td v-if="allowSel" class="selCheckbox px-1"
            :class="{selected: attr.isSelected}"><input type="checkbox" v-model="attr.isSelected"/></td>
        <td class="name">{{attr.name}}{{"value" in attr ? "=" : ""}}</td>
        <td class="value">
            <template v-if="'value' in attr">
                <a v-if="attr.name === 'href' || attr.name === 'src' || attr.name === 'data-src'"
                   :href="_uriResolver(attr.value, _GetMode(attr) === Mode.DELETED ? 0 : 1)"
                   rel="noreferrer">{{attr.value}}</a>
                <template v-else>{{attr.value}}</template>
            </template>
        </td>
    </tr>
    </tbody></table>
    <template v-if="hasChildren && isExpanded">
        <wdk-html-element v-for="child in element.children" :element="child" 
                          :uriResolver="uriResolver" :allowSel="allowSel" :key="child.id" />
    </template>
</div>
</template>

<script>

export const Mode = {
    INSERTED: 0,
    DELETED: 1,
    EQUAL: 2
}

export default {
    name: "WdkHtmlElement",
    
    props: {
        element: {
            default: null
        },
        uriResolver: {
            type: Function,
            default: (path, side) => path
        },
        allowSel: {
            default: false
        }
    },

    data() {
        return {
            isExpanded: false,
            Mode: Mode
        }
    },

    computed: {
        _uriResolver() {
            if (this.uriResolver === null) {
                return (path, side) => path
            }
            return this.uriResolver()
        },

        isExpandable() {
            return "children" in this.element || "attrs" in this.element
        },

        isText() {
            return "text" in this.element
        },

        hasChildren() {
            return "children" in this.element
        },

        hasAttributes() {
            return "attrs" in this.element
        },

        name() {
            return this.element.name !== "" ? this.element.name : "ROOT"
        },

        tags() {
            return "tags" in this.element ? this.element.tags : []
        },

        mode() {
            return this._GetMode(this.element)
        },

        isClosing() {
            if (!("isClosing" in this.element)) {
                return false
            }
            return this.element.isClosing
        },

        isClosed() {
            if (!("isClosed" in this.element)) {
                return true
            }
            return this.element.isClosed
        },

        isChanged() {
            if (!("isChanged" in this.element)) {
                return false
            }
            return this.element.isChanged
        }
    },

    methods: {
        _ToggleExpand() {
            if (this.isExpandable) {
                this.isExpanded = !this.isExpanded
            }
        },

        _GetMode(obj) {
            if (!("mode" in obj)) {
                return Mode.EQUAL
            }
            return obj.mode
        }
    }
}

</script>

<style scoped lang="less">

div.WdkHtmlElement {
    border: #e0e0e0 solid 1px;
    padding: 2px;

    &:hover {
        border: #a8a8a8 solid 1px;
    }

    div.text {
        border: #b0b0b0 solid 1px;
        border-radius: 4px;
        padding: 0 6px;
        background: #d7d7d7;
        margin: 2px;
        font-size: 0.9em;

        &.modeInserted {
            background: #a9e6b0;
        }

        &.modeDeleted {
            background: #e6c4cb;
        }
    }

    span.name {
        font-family: monospace;
        padding-left: 1em;
        padding-right: 1em;

        &:first-child {
            margin-left: 2px;
        }

        &.collapsed {
            color: #909090;
        }

        &.expandable {
            cursor: pointer;
        }

        &.changed {
            background: #c1e5ff;
        }
    }

    .expandButton {
        color: #808080;
        border: #e0e0e0 solid 1px;
        border-radius: 2px;
        display: inline-block;
        cursor: pointer;
        width: 2em;
        height: 2em;
        position: relative;
        font-size: 8px;
        vertical-align: middle;
        margin-left: 4px;

        &:after,
        &:before {
            content: "";
            display: block;
            background-color: grey;
            position: absolute;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
        }

        &.collapsed {
            &:before {
                height: 1em;
                width: 2px;
            }

            &:after {
                height: 2px;
                width: 1em;
            }
        }

        &.expanded {
            &:after {
                height: 0.2em;
                width: 1em;
            }
        }
    }

    table.attributes {
        margin-bottom: 0.2rem;
        font-family: monospace;
        margin-left: 2px;

        td {
            padding: 0.1rem;

            &.value {
                padding-left: 0.5rem;
                width: 100%;
            }
        }
    }

    .modeInserted {
        background: #baffc7;
    }

    .modeDeleted {
        background: #ffdbe3;
    }

    .selCheckbox {
        opacity: 0.3;
        vertical-align: middle;

        &:hover, &.selected {
            opacity: 1.0;
        }
    }
}

</style>