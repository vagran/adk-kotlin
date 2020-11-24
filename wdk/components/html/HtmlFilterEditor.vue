<!--
  - This file is part of ADK project.
  - Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
  - See LICENSE file for full license details.
  -->

<template>
<q-card class="WdkHtmlFilterEditor">
    <q-card-section class="q-pa-sm"><q-item-label class="text-h6">{{title}}</q-item-label></q-card-section>
    <q-separator />

    <q-card-section>
        <wdk-status-view :status="status" />
        <wdk-html-filter v-if="filterTree !== null" :data="filterTree" @nodeClicked="_OnNodeClicked"/>
    </q-card-section>

    <q-card-actions>
        <q-btn label="Clear" color="negative" @click="_Clear" />
        <q-btn label="Add" color="primary" @click="_Add" />
    </q-card-actions>

    <q-card-section>
        <wdk-entities-list v-if="filterTree !== null" class="q-my-sm"
                           title="Tag transforms"
                           :editable="true"
                           :monospaceFont="true"
                           :providedEntities="transforms"
                           :itemDisplayName="_TransformDisplayName"
                           :newNameRequired="true"
                           :newUrl="`${dcoEndpoint}/AddTransform`"
                           :deleteReq="_DeleteTransform"
                           @onAdded="name => _OnModified()"
                           @onDeleted="info => _OnModified()"
                           @onEdit="_EditTransform" />
    </q-card-section>

    <!-- Filter tree node editing dialog -->
    <q-dialog v-model="editDialog" persistent>
        <q-card style="width: 80%; max-width: 80%;" class="WdkHtmlFilterEditor q-my-sm">
            <q-card-section class="row items-center q-pb-none">
                <div class="text-h6">Edit filter node</div>
                <q-space />
                <q-btn icon="close" flat round dense v-close-popup />
            </q-card-section>
            <q-card-section class="syntaxHelp q-py-none">
                <q-list>
                    <q-expansion-item popup dense icon="help" label="Syntax help">
                        <p class="example">selector[; auxSelector][, selector[; auxSelector]]...</p>
                        <p class="example">elA > * > elB#someId.someClass.anotherClass@elementTag elC@~textTag</p>
                        <p class="example">[attrName="attrValue"] .someClass[@tagName:attrName]</p>
                        <p class="example">a:nth-child(1) b:nth-of-type(2) c:nth-last-child(1) d:nth-last-of-type(2)</p>
                        <p>Selector has limited CSS selector syntax. Auxiliary selector used for
                            main selector adjusting (typically in alternative form, e.g. XPath).
                            XPath selector should be enclosed in curved braces. Otherwise it is
                            treated as CSS selector. Multiple selectors are merged into one filter
                            instance.</p>
                    </q-expansion-item>
                </q-list>
            </q-card-section>
            <q-card-section>
                <wdk-status-view :status="editStatus" class="q-my-sm"/>
                <q-input v-if="editContent !== null" type="textarea" filled style="width: 100%;"
                          v-model="editContent" autofocus/>
            </q-card-section>
            <q-card-actions>
                <q-btn v-if="editNodeId !== null" label="Delete" color="negative"
                       @click="_OnNodeDelete(false)" />
                <q-btn v-if="editNodeId !== null" label="Delete branch" color="negative"
                       @click="_OnNodeDelete(true)" />
                <q-btn label="Save changes" color="primary" @click="_OnNodeSave" />
            </q-card-actions>
        </q-card>
    </q-dialog>

    <!-- Tag transform editing dialog -->
    <q-dialog v-model="transformEditDialog" persistent>
        <q-card style="width: 80%; max-width: 80%;" class="WdkHtmlFilterEditor q-my-sm">
            <q-card-section class="row items-center q-pb-none">
                <div class="text-h6">Edit tag transform</div>
                <q-space />
                <q-btn icon="close" flat round dense v-close-popup />
            </q-card-section>
            <q-card-section>
                <wdk-status-view :status="transformEditStatus"/>
                <div v-if="transformEditItem !== null">
                    For tag <span class="TagName">{{transformEditItem.tagName}}</span>
                    <q-input v-model="transformEditItem.transform.matchPattern"
                             label="Matching pattern" />
                    <q-input v-model="transformEditItem.transform.replacePattern"
                             label="Replacing pattern" />
                </div>
            </q-card-section>
            <q-card-actions>
                <q-btn color="primary" label="Save changes" @click="_SaveTransform" />
            </q-card-actions>
        </q-card>
    </q-dialog>
</q-card>
</template>

<script>
/**
 * Events:
 *  modified - filter was modified.
 */
import { PostRequest } from "../../common/utils"
import WdkStatusView from "../StatusView"
import WdkEditableProperties from "../EditableProperties"
import WdkHtmlFilter from "./HtmlFilter"

export default {
    name: "WdkHtmlFilterEditor",


    components: { WdkHtmlFilter, WdkStatusView, WdkEditableProperties },

    props: {
        dcoEndpoint: {
            type: String,
            required: true
        },
        editable: {
            default: true
        },
        title: {
            default: "HTML filter"
        },
        /* ID for embedded form. */
        id: {
            default: "filterEditor"
        },
    },

    data() {
        return {
            filterTree: null,
            status: null,
            editStatus: null,
            editDialog: false,
            editNodeId: null,
            editContent: null,
            transformEditStatus: null,
            transformEditItem: null,
            transformEditDialog: false
        }
    },

    computed: {
        transforms() {
            let result = []
            for (let tagName in this.filterTree.transforms) {
                if (!this.filterTree.transforms.hasOwnProperty(tagName)) {
                    continue
                }
                result.push({
                                tagName: tagName,
                                transform: this.filterTree.transforms[tagName]
                            })
            }
            return result
        }
    },

    methods: {
        async Update() {
            try {
                this.filterTree = await PostRequest(this.dcoEndpoint)
                this.status = null
            } catch(error) {
                console.error(error)
                this.status = error
            }
        },

        async _OnModified() {
            this.$emit("modified")
            await this.Update()
        },

        async _OnNodeClicked(node) {
            if (node.id === 0 || !this.editable) {
                return
            }
            this.editNodeId = node.id
            this.editStatus = "%>Fetching selectors..."
            this.editDialog = true
            try {
                const result = await PostRequest(`${this.dcoEndpoint}/BeginEdit`, {nodeId: node.id})
                this.editStatus = null
                this.editContent = result.join(",\n")
            } catch(error) {
                console.error(error)
                this.editStatus = error
            }
        },

        async _OnNodeSave() {
            this.editStatus = "%>Saving..."
            try {
                if (this.editNodeId === null) {
                    await PostRequest(`${this.dcoEndpoint}/Add`, {
                        selectors: this.editContent
                    })
                } else {
                    await PostRequest(`${this.dcoEndpoint}/Modify`, {
                        nodeId: this.editNodeId,
                        selectors: this.editContent
                    })
                }
                this.editStatus = null
                this.editDialog = false
                await this._OnModified()
            } catch (error) {
                console.error(error)
                this.editStatus = error
            }
        },

        async _OnNodeDelete(deleteBranch) {
            if (!confirm("Are you sure you want to delete the node?")) {
                return
            }
            this.editStatus = "%>Deleting..."
            try {
                await PostRequest(`${this.dcoEndpoint}/Delete`, {
                    nodeId: this.editNodeId,
                    deleteBranch: deleteBranch
                })
                this.editStatus = null
                this.editDialog = false
                await this._OnModified()
            } catch(error) {
                console.error(error)
                this.editStatus = error
            }
        },

        async _Clear() {
            if (!confirm("Are you sure you want to clear the filter?")) {
                return
            }
            this.status = "%>Clearing..."
            try {
                await PostRequest(`${this.dcoEndpoint}/Clear`)
                this.status = null
                await this._OnModified()
            } catch(error) {
                console.error(error)
                this.status = error
            }
        },

        _Add() {
            this.editNodeId = null
            this.editStatus = null
            this.editContent = ""
            this.editDialog = true
        },

        _TransformDisplayName(t) {
            return `${t.tagName}: ${t.transform.matchPattern} -> ${t.transform.replacePattern}`
        },

        _DeleteTransform(t) {
            return {
                url: `${this.dcoEndpoint}/DeleteTransform`,
                params: { tagName: t.tagName }
            }
        },

        _EditTransform(t) {
            this.transformEditItem = {
                tagName: t.tagName,
                transform: Object.assign({}, t.transform)
            }
            this.transformEditStatus = null
            this.transformEditDialog = true
        },

        async _SaveTransform() {
            this.transformEditStatus = "%>Applying..."
            try {
                await PostRequest(`${this.dcoEndpoint}/ModifyTransform`, {
                    tagName: this.transformEditItem.tagName,
                    matchPattern: this.transformEditItem.transform.matchPattern,
                    replacePattern: this.transformEditItem.transform.replacePattern
                })
                this.transformEditStatus = null
                this.transformEditDialog = false
                await this._OnModified()
            } catch(error) {
                console.error(error)
                this.transformEditStatus = error
            }
        }
    },

    mounted() {
        this.Update()
    }
}

</script>

<style scoped lang="less">

.WdkHtmlFilterEditor {
    .TagName {
        font-weight: 600;
        color: #aa6739;
    }

    .syntaxHelp {
        p {
            margin: 0 8px;
            &.example {
                font-family: "Roboto Mono", monospace;
                color: #005e5e;
            }
        }
    }
}

</style>