<!--
  - This file is part of ADK project.
  - Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
  - See LICENSE file for full license details.
  -->

<template>

<q-card class="WdkEntitiesList">
    <template v-if="title !== null">
        <q-card-section class="q-pa-sm"><q-item-label class="text-h6">{{title}}</q-item-label></q-card-section>
        <q-separator />
    </template>

    <q-card-section class="q-pa-sm">
    <wdk-status-view :status="status" class="q-my-sm" />

    <div v-if="entities !== null && entities.length === 0">{{emptyMessage}}</div>

    <table v-else-if="entities !== null" class="table table-sm">
        <tbody>
        <template v-for="info in entities">
            <tr :key="info[idField]">
                <td v-if="prefixInfoSpecified">
                    <slot name="prefixInfo" :info="info"/>
                </td>
                <td :class="{monospaceFont: monospaceFont}">
                    <span v-if="itemLink === null && itemLinkTo === null" class="item"
                          :class="{editable: editable || editFields !== null}"
                          @click="_EditItem(info)">{{itemDisplayName(info)}}</span>
                    <a v-else-if="itemLink !== null" class="item" rel="noreferrer" :href="itemLink(info)">{{itemDisplayName(info)}}</a>
                    <router-link :to="itemLinkTo(info)">{{itemDisplayName(info)}}</router-link>
                    <span v-if="editable || editFields !== null" class="button text-primary"
                          @click="_EditItem(info)"><q-icon name="edit"/></span>
                    <span v-if="deleteReq !== null"
                          class="button text-negative"
                          @click="_DeleteEntity(info)"><q-icon name="delete_forever"/></span>
                </td>
                <td v-if="suffixInfoSpecified">
                    <slot name="suffixInfo" :info="info"/>
                </td>
            </tr>
            <transition name="dropdown">
                <tr v-if="editItem !== null && info[idField] === editItem[idField]" class="editItem">
                    <td colspan="3">
                        <div class="editItemContainer">
                            <wdk-tatus-view :status="editStatus" class="my-2" :isToast="true" />
                            <h1>Edit entity</h1>
                            <wdk-editable-properties :data="editItem" :fields="editFields"
                                                     @updated="_UpdateItem"/>
                            <div class="closeLink">
                                <a class="closeLink" href="#" @click="_EditClose">Close</a>
                            </div>
                        </div>
                    </td>
                </tr>
            </transition>
        </template>
        </tbody>
    </table>

    <form v-if="newUrl !== null" class="my-2" @submit.prevent="_NewEntity()">
        <div v-if="newItemData !== null" class="newItemContainer my-2">
            <h1>Create new entity</h1>
            <editable-properties :data="newItemData" :fields="editFields" @updated="_UpdateNewItem"/>
            <div class="closeLink">
                <a class="closeLink" href="#" @click="newItemData = null">Close</a>
            </div>
        </div>
        <div class="form-row">
            <div v-if="newNameRequired" class="col-auto">
                <input type="text" class="form-control" v-model="newName"
                       :placeholder="newNamePlaceholder" />
            </div>
            <div class="col-auto">
                <q-btn outline type="submit" padding="sm">Add</q-btn>
            </div>
        </div>
    </form>

    <wdk-message-box ref="msgBox" />
    </q-card-section>
</q-card>

</template>

<script>
import WdkStatusView from "./StatusView"
import WdkMessageBox from "./MessageBox"
import WdkEditableProperties from "./EditableProperties"

import { REFRESH_INTERVAL, PollTimer, PostRequest } from "../common/utils"


export default {
    name: "WdkEntitiesList",
    components: {WdkStatusView, WdkMessageBox, WdkEditableProperties},
    props: {
        title: {
            default: null
        },
        emptyMessage: {
            default: "No items"
        },
        /** List items refresh interval in milliseconds */
        refreshInterval: {
            default: () => REFRESH_INTERVAL
        },
        /** URL for regular entities fetching. Use providedEntities if null. */
        fetchUrl: {
            default: null
        },
        /** Externally provided entities list, used only if fetchUrl is null. */
        providedEntities: {
            default: null
        },
        itemDisplayName: {
            type: Function,
            default(info) {
                if (this.isLocalId) {
                    return "#" + app.LocalIdShortDisplay(info[this.idField]);
                }
                return "#" + info[this.idField];
            }
        },
        monospaceFont: {
            type: Boolean,
            default: false
        },
        /** Function which maps item to link URL. Null if no link needed. */
        itemLink: {
            type: Function,
            default: null
        },
        /** Function which maps item to link URL. Null if no link needed. Uses router-link tag
         * instead of regular link.
         */
        itemLinkTo: {
            type: Function,
            default: null
        },
        /** Item can be edited by clicking on it. onEdit event is emitted. Mutually exclusive
         * with editFields. Separate edit button is shown when itemLink specified.
         */
        editable: {
            type: Boolean,
            default: false
        },
        /** Fields descriptors for EditableProperties component if inline editing allowed. */
        editFields: {
            type: [Object, Array],
            default: null
        },
        newUrl: {
            default: null
        },
        newNameRequired: {
            default: false
        },
        newNamePlaceholder: {
            default: "New item name"
        },
        /** New item template. This enables new item form. editFields are used if specified. */
        newItem: {
            type: [Object, Function],
            default: null
        },
        /** Should return request object with "url" and optional "params" object. */
        deleteReq: {
            type: Function,
            default: null
        },
        /** Should return request object with "url" and optional "params" object.
         * Arguments: (entity, fieldName, value)
         */
        modifyReq: {
            type: Function,
            default: null
        },
        /** Display as shortened LocalId by default. */
        isLocalId: {
            default: false
        },
        /** Comparator function to sort entities. Use fetch order if not specified. */
        sortFunc: {
            type: Function,
            default: null
        },
        idField: {
            type: String,
            default: "id"
        }
    },

    data() {
        return {
            status: {
                svAggregated: true,
                fetch: "%>Loading",
                op: null
            },
            entities: null,
            newName: null,
            editItem: null,
            editStatus: null,
            newItemData: null
        }
    },

    computed: {
        prefixInfoSpecified() {
            return !!this.$scopedSlots["prefixInfo"]
        },

        suffixInfoSpecified() {
            return !!this.$scopedSlots["suffixInfo"]
        }
    },

    methods: {
        _Fetch() {
            if (this.fetchUrl !== null) {
                return PostRequest(this.fetchUrl)
                    .then(result => {
                        this.status.fetch = null
                        if (this.sortFunc !== null) {
                            result.sort(this.sortFunc)
                        }
                        this.entities = result
                        this.$emit("fetched", result)
                    })
                    .catch(error => {
                        console.error(error)
                        this.status.fetch = error
                    })
            } else {
                this.status.fetch = null
                this.entities = this.providedEntities
                return new Promise(resolve => resolve())
            }
        },

        _NewEntity() {
            if (this.newItem !== null) {
                if (this.newItemData === null) {
                    if (this.newItem instanceof Function) {
                        try {
                            this.newItemData = this.newItem()
                        } catch (e) {
                            this.status = e
                            return
                        }
                    } else {
                        this.newItemData = Object.assign({}, this.newItem)
                    }
                    return
                }
                this.opStatus = "%>Processing..."
                PostRequest(this.newUrl, this.newItemData)
                    .done(result => {
                        this.status.op = null
                        this.$emit("onAdded", this.newItemData)
                        this.newItemData = null
                        this._Fetch()
                    })
                    .fail(error => {
                        console.error(error)
                        this.status.op = error
                    })
                return
            }

            let name = undefined
            if (this.newNameRequired) {
                if (this.newName === null) {
                    this.$refs.msgBox.Show("error", "Name should be specified",
                                           "New item creation")
                    return
                }
                name = this.newName
                this.newName = null
            }

            this.opStatus = "%>Processing..."
            PostRequest(this.newUrl, name)
                .done(result => {
                    this.status.op = null
                    this.$emit("onAdded", name)
                    this._Fetch()
                })
                .fail(error => {
                    console.error(error)
                    this.status.op = error
                })
        },

        _DeleteEntity(info) {
            if (this.deleteReq === null) {
                return
            }
            if (!confirm("Are you sure you want to delete the item?")) {
                return
            }
            let req = this.deleteReq(info)
            this.status.op = "%>Processing..."
            PostRequest(req.url, req.params)
                .done(result => {
                    this.status.op = null
                    this.$emit("onDeleted", info)
                    this._Fetch()
                })
                .fail(error => {
                    console.error(error)
                    this.status.op = error
                })
        },

        _EditItem(info) {
            if (this.editable) {
                this.$emit("onEdit", info)
            } else if (this.editFields !== null) {
                this.editItem = info
            }
        },

        _EditClose() {
            this.editItem = null
            this.editStatus = null
        },

        _UpdateItem(field, value) {
            if (this.editItem === null) {
                return
            }
            this.editItem[field] = value
            this.$emit("onModify", this.editItem, field, value)
            if (this.modifyReq !== null) {
                let req = this.modifyReq(this.editItem, field, value)
                PostRequest(req.url, req.params)
                    .done(result => {
                        this.editStatus = null
                        this._Fetch()
                    })
                    .fail(error => {
                        console.error(error)
                        this.editStatus = error
                    })
            }
        },

        _UpdateNewItem(field, value) {
            this.newItemData[field] = value
        }
    },

    mounted() {
        this.pollTimer = new PollTimer(this.refreshInterval)
        this.pollTimer.Poll("fetch", () => this._Fetch())
    },

    beforeDestroy() {
        this.pollTimer.Stop()
    }
}

</script>

<style scoped lang="less">
.WdkEntitiesList {
    table {
        width: auto;
        margin-bottom: 0;
    }

    div.card-body {
        padding: 0.6rem;
    }

    .button {
        opacity: 0.3;
        cursor: pointer;
        border-radius: 0.2em;
        padding: 0 0.2em;
        margin-left: 0.2em;
        border-width: 1px;
        border-style: solid;

        &:hover {
            opacity: 1.0;
        }
    }

    span.item.editable {
        cursor: pointer;

        &:hover {
            background: #e0e0e0;
            text-decoration: underline;
        }
    }

    td.monospaceFont .item {
        font-family: var(--font-family-monospace);
    }

    tr.editItem {
        & > td {
            border: solid 1px #dddddd;

            h1 {
                font-size: 0.9rem;
                font-style: italic;
                opacity: 0.8;
                margin: 2px 5px 4px;
            }

            div.closeLink {
                text-align: right;
                margin-right: 10px;
                font-size: 0.9rem;
            }
        }

        &.dropdown-enter-active {
            & > td {
                div.editItemContainer {
                    max-height: 100px;
                    overflow: hidden;
                    transition: max-height 0.2s ease-in;
                }
            }
            transition: all 0.2s ease-in;
        }

        &.dropdown-enter {
            & > td {
                div.editItemContainer {
                    max-height: 0;
                    overflow: hidden;
                }
            }
        }
    }

    .newItemContainer {
        border: solid 1px #dddddd;

        h1 {
            font-size: 0.9rem;
            font-style: italic;
            opacity: 0.8;
            margin: 2px 5px 4px;
        }

        div.closeLink {
            text-align: right;
            margin-right: 10px;
            font-size: 0.9rem;
        }
    }
}
</style>
