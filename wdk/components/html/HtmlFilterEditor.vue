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
        Update() {
            return PostRequest(this.dcoEndpoint)
                .then(result => {
                    this.status = null
                    this.filterTree = result
                })
                .catch(error => {
                    console.error(error)
                    this.status = error
                })
        },

        _OnModified() {
            this.$emit("modified")
            this.Update()
        },

        _OnNodeClicked(node) {
            if (node.id === 0 || !this.editable) {
                return
            }
            this.editNodeId = node.id
            this.editStatus = "%>Fetching selectors..."
            this.editDialog = true
            PostRequest(`${this.dcoEndpoint}/BeginEdit`, {nodeId: node.id})
                .then(result => {
                    this.editStatus = null
                    this.editContent = result.join(",\n")
                })
                .catch(error => {
                    console.error(error)
                    this.editStatus = error
                })
        },

        _OnNodeSave() {
            this.editStatus = "%>Saving..."
            let req
            if (this.editNodeId === null) {
                req = PostRequest(`${this.dcoEndpoint}/Add`, {
                    selectors: this.editContent
                })
            } else {
                req = PostRequest(`${this.dcoEndpoint}/Modify`, {
                    nodeId: this.editNodeId,
                    selectors: this.editContent
                })
            }
            req.then(result => {
                this.editStatus = null
                this.editDialog = false
                this.Update()
                this.$emit("modified")
            })
                .catch(error => {
                    console.error(error)
                    this.editStatus = error
                })
        },

        _OnNodeDelete(deleteBranch) {
            if (!confirm("Are you sure you want to delete the node?")) {
                return
            }
            this.editStatus = "%>Deleting..."
            PostRequest(`${this.dcoEndpoint}/Delete`, {
                nodeId: this.editNodeId,
                deleteBranch: deleteBranch
            })
                .then(result => {
                    this.editStatus = null
                    this.editDialog = false
                    this.Update()
                    this.$emit("modified")
                })
                .catch(error => {
                    console.error(error)
                    this.editStatus = error
                })
        },

        _Clear() {
            if (!confirm("Are you sure you want to clear the filter?")) {
                return
            }
            this.status = "%>Clearing..."
            PostRequest(`${this.dcoEndpoint}/Clear`)
                .then(result => {
                    this.status = null
                    this.Update()
                    this.$emit("modified")
                })
                .catch(error => {
                    console.error(error)
                    this.status = error
                })
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

        _SaveTransform() {
            this.transformEditStatus = "%>Applying..."
            PostRequest(`${this.dcoEndpoint}/ModifyTransform`, {
                tagName: this.transformEditItem.tagName,
                matchPattern: this.transformEditItem.transform.matchPattern,
                replacePattern: this.transformEditItem.transform.replacePattern
            })
                .then(result => {
                    this.transformEditStatus = null
                    this.transformEditDialog = false
                    this.Update()
                    this.$emit("modified")
                })
                .catch(error => {
                    console.error(error)
                    this.transformEditStatus = error
                })
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
}

</style>