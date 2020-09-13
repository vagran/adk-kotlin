<template>

<q-list v-if="hasItems" bordered class="root rounded-borders" :class="{toast: isToast}">
    <q-item v-if="items.length > 1" class="top" >
        <a href="#" @click.prevent="_DismissAll" class="text-secondary">Dismiss all</a>
    </q-item>
    <q-item v-for="item in visibleItems" :key="item.id"
            :class="[`bg-${_GetBgColor(item.level)}`, `text-${_GetTextColor(item.level)}`]">
        <q-item-section v-if="item.level === 'progress' || _GetIconName(item.level) !== null"
                        avatar class="iconContainer">
            <q-spinner v-if="item.level === 'progress'" size="sm" />
            <q-icon v-else :name="_GetIconName(item.level)" size="sm" />
        </q-item-section>
        <q-item-section>
            <div v-if="item.title !== null" class="title">{{item.title}}</div>
            <div>
                <span v-if="item.isHtml" v-html="item.text" />
                <template v-else>{{item.text}}</template>
            </div>
            <details v-if="item.details !== null">
                <summary>Details</summary>
                <div class="details">{{item.details}}</div>
            </details>
        </q-item-section>
        <q-item-section side top>
            <q-badge v-if="item.count > 1" class="count">{{item.count}}</q-badge>
            <q-btn round outline class="close" icon="close" :color="_GetTextColor(item.level)"
                   size="xs" @click="_OnDismiss(item.id)" />
        </q-item-section>
    </q-item>
    <q-item v-if="hasCollapsedItems" class="bottom">
        <a href="#" @click.prevent="isExpanded = true" class="text-secondary">Expand</a>
        <q-badge v-if="collapsedErrorCount !== 0" class="q-mx-xs"
                 :class="[`bg-${_GetBgColor('error')}`, `text-${_GetTextColor('error')}`]">{{collapsedErrorCount}}</q-badge>
        <q-badge v-if="collapsedWarningCount !== 0" class="q-mx-xs"
                 :class="[`bg-${_GetBgColor('warning')}`, `text-${_GetTextColor('warning')}`]">{{collapsedWarningCount}}</q-badge>
        <q-badge v-if="collapsedInfoCount !== 0" class="q-mx-xs"
                 :class="[`bg-${_GetBgColor('info')}`, `text-${_GetTextColor('info')}`]">{{collapsedInfoCount}}</q-badge>
        <q-badge v-if="collapsedSuccessCount !== 0" class="q-mx-xs"
                 :class="[`bg-${_GetBgColor('success')}`, `text-${_GetTextColor('success')}`]">{{collapsedSuccessCount}}</q-badge>
        <q-badge v-if="collapsedOtherCount !== 0" class="q-mx-xs"
                 :class="[`bg-${_GetBgColor('secondary')}`, `text-${_GetTextColor('secondary')}`]">{{collapsedOtherCount}}</q-badge>
    </q-item>
</q-list>

</template>

<script>

export default {
    name: "WdkStatusView",

    props: {
        /* Can be either status object, or array, or aggregated object (primary types).
         *
         * Each item of an array can be any of the primary types.
         *
         * Aggregated object should have property "svAggregated: true". In such case all its
         * rest properties can be any of the primary types.
         *
         * Status object can be one of:
         *
         * * String. In such case it is interpreted as status message. The message can have
         *      a prefix for specifying level. Possible values:
         *    * "E>" - error
         *    * "W>" - warning
         *    * "I>" - info
         *    * "S>" - success
         *    * "%>" - progress (spinner is displayed)
         *    * "P>" - primary
         *    * "L>" - light
         *    * "D>" - dark
         *    * no prefix - secondary
         *
         * * Status item object:
         *   {
         *      text: <message text>,
         *      level: <optional message level, default is secondary>,
         *      details: <optional details text>,
         *      isHtml: <optional indication to interpret text as HTML, default is false>,
         *      title: <optional message title>,
         *      expires: <optional expiration timeout override, seconds, zero for disabling>
         *   }
         *
         * * Error instance
         *
         * * jQuery XHR error.
         *
         * * jQuery XHR result with ADK domain server JSON error.
         *
         * * Status wrapper, specifies some common properties for embedded status object(s).
         *      The properties affect all the wrapped status objects, if they are not overridden
         *      in a particular object.
         *  {
         *      svWrapped: true,
         *      s: <wrapped status, any of the primary types>,
         *      isHtml: <optional indication to interpret text as HTML, default is false>,
         *      title: <optional message title>,
         *      expires: <optional expiration timeout override, seconds, zero for disabling>
         *  }
         */
        "status": {
            default: null
        },

        /* Default expiration time for individual messages, seconds. Zero to disable expiration
         * by default.
         */
        "expirationTimeout": {
            default: 0
        },

        /* Maximal number of items to display when not expanded. */
        "maxCollapsedCount": {
            default: 4
        },

        /* Display as floating toast. */
        "isToast": {
            default: false
        }
    },

    data() {
        return {
            /* {
             *      id: <assigned ID>,
             *      text: <text>,
             *      level: <level>,
             *      details: <details text, may be null>,
             *      isHtml: <{boolean}>,
             *      title: <title text, may be null>,
             *      timestamp: <Date.getTime()>,
             *      count: <number of aggregated messages>,
             *      expires: expiration timestamp, zero for persistent message.
             * }
             */
            items: [],

            isExpanded: false,

            /** IDs for items inserted via "status" property. */
            propItems: []
        }
    },

    computed: {
        hasItems() {
            return this.items.length !== 0
        },

        visibleItems() {
            if (this.isExpanded || this.items.length <= this.maxCollapsedCount) {
                return this.items
            }
            return this.items.slice(0, this.maxCollapsedCount)
        },

        hasCollapsedItems() {
            return !this.isExpanded && this.items.length > this.maxCollapsedCount
        },

        collapsedSuccessCount() {
            return this._CollapsedCount("success")
        },

        collapsedInfoCount() {
            return this._CollapsedCount("info")
        },

        collapsedWarningCount() {
            return this._CollapsedCount("warning")
        },

        collapsedErrorCount() {
            return this._CollapsedCount("error")
        },

        collapsedOtherCount() {
            return this._CollapsedCount(null)
        }
    },

    watch: {
        status: {
            deep: true,
            handler(status) {
                this._ApplyCurStatus(status)
            }
        }
    },

    mounted() {
        this.curId = 1
        this.timerId = null
        this.curExpireItem = null
        this._ApplyCurStatus(this.status)
    },

    beforeDestroy() {
        if (this.timerId !== null) {
            clearTimeout(this.timerId)
            this.timerId = null
            this.curExpireItem = null
        }
    },

    methods: {
        /**
         * Push some status message(s). The pushed messages are not tracked, so if the passed
         * object is changed after the call, it does not affect the displayed status.
         * @param status
         * @param title Optional title override. Has lower priority than title attributes in
         *  status objects.
         */
        Push(status, title = null) {
            this._PushImpl(status, false, title)
            this._CommitItems()
        },

        _ApplyCurStatus(status) {
            if (status === null) {
                while (this.propItems.length !== 0) {
                    this._RemoveItem(this.propItems[0])
                }
                this._CommitItems()
                return
            }
            let mergedIds = this._PushImpl(status, true)
            let removeList = []
            for (let id of this.propItems) {
                if (!mergedIds.includes(id)) {
                    removeList.push(id)
                }
            }
            for (let id of removeList) {
                this._RemoveItem(id)
            }
            for (let id of mergedIds) {
                if (!this.propItems.includes(id)) {
                    this.propItems.push(id)
                }
            }
            this._CommitItems()
        },

        /**
         * @param status Any supported status object.
         * @param doMerge Do not increment aggregation counter and do not update timestamp if
         *      the same message found in current messages list.
         * @param title Title override.
         * @return Array of merged item IDs if `doMerge` is true.
         */
        _PushImpl(status, doMerge, title = null) {
            let items = this._ParseStatus(status, title)
            let mergedItems = doMerge ? [] : null
            for (let item of items) {
                let existingItem = this._FindEqualItem(item)
                if (existingItem === null) {
                    this.items.push(item)
                    this._CheckExpireNew(item.expires)
                    if (doMerge) {
                        mergedItems.push(item.id)
                    }
                } else if (!doMerge) {
                    existingItem.timestamp = item.timestamp
                    existingItem.expires = item.expires
                    existingItem.count++
                    this._CheckExpireNew(existingItem.expires)
                } else {
                    mergedItems.push(existingItem.id)
                }
            }
            return mergedItems
        },

        _CheckExpireNew(expires) {
            if (expires !== 0 && this.curExpireItem !== null &&
                expires < this.curExpireItem.expires) {

                this.curExpireItem = null
                clearTimeout(this.timerId)
                this.timerId = null
            }
        },

        _SortItems() {
            this.items.sort((i1, i2) => i2.timestamp - i1.timestamp)
        },

        _RemoveItem(id) {
            let delIdx = -1
            for (let idx = 0; idx < this.items.length; idx++) {
                let item = this.items[idx]
                if (item.id === id) {
                    delIdx = idx
                    break
                }
            }
            let propDelIdx = -1
            for (let idx = 0; idx < this.propItems.length; idx++) {
                if (this.propItems[idx] === id) {
                    propDelIdx = idx
                    break
                }
            }
            if (propDelIdx !== -1) {
                this.propItems.splice(propDelIdx, 1)
            }
            if (delIdx === -1) {
                return
            }
            this.items.splice(delIdx, 1)
            if (this.items.length <= this.maxCollapsedCount) {
                this.isExpanded = false
            }
            if (this.curExpireItem !== null && this.curExpireItem.id === id) {
                this.curExpireItem = null
                clearTimeout(this.timerId)
                this.timerId = null
            }
        },

        /**
         * @param item Reference item to find equal one in the current items list.
         * @return Found item, null if not found.
         */
        _FindEqualItem(item) {
            for (let i of this.items) {
                if (this._ItemEquals(item, i)) {
                    return i
                }
            }
            return null
        },

        /** Check it two items look equals. */
        _ItemEquals(item1, item2) {
            return item1.text === item2.text &&
                item1.level === item2.level &&
                item1.details === item2.details &&
                item1.isHtml === item2.isHtml &&
                item1.title === item2.title
        },

        _ParseStatus(status, title = null) {
            let result = []
            let ctx = {
                title: title,
                isHtml: false,
                timestamp: new Date().getTime(),
                expires: this.expirationTimeout
            }
            this._ParseStatusImpl(status, ctx, result)
            return result
        },

        /** Parse status object of any supported type into list of status items.
         * @param status Any supported status object.
         * @param ctx Context object with shared properties.
         * @param result Array to append result to.
         */
        _ParseStatusImpl(status, ctx, result) {
            if (status === null) {
                return
            }

            let type = typeof status

            if (Array.isArray(status)) {
                for (let index = 0; index < status.length; index++) {
                    let item = status[index]
                    this._ParseStatusImpl(item, ctx, result)
                }

            } else if (type === "object") {
                if (status.hasOwnProperty("svAggregated") && status.svAggregated === true) {
                    /* Aggregated status object. */
                    for (let propName in status) {
                        if (!status.hasOwnProperty(propName) || propName === "svAggregated") {
                            continue
                        }
                        this._ParseStatusImpl(status[propName], ctx, result)
                    }

                } else if (status.hasOwnProperty("svWrapped") && status.svWrapped === true) {
                    /* Wrapper object. */
                    let newCtx = Object.assign({}, ctx)
                    if (status.hasOwnProperty("isHtml")) {
                        newCtx.isHtml = status.isHtml
                    }
                    if (status.hasOwnProperty("title")) {
                        newCtx.title = status.title
                    }
                    if (status.hasOwnProperty("expires")) {
                        newCtx.expires = status.expires
                    }
                    this._ParseStatusImpl(status.s, newCtx, result)

                } else {
                    let item = this._ParseStatusItem(status, ctx)
                    if (item !== null) {
                        result.push(item)
                    }
                }

            } else {
                let item = this._ParseStatusItem(status, ctx)
                if (item !== null) {
                    result.push(item)
                }
            }
        },

        /** Parse one of supported non-aggregated status object into status item. */
        _ParseStatusItem(status, ctx) {
            let result = {
                details: null,
                isHtml: ctx.isHtml,
                title: ctx.title,
                timestamp: ctx.timestamp,
                expires: ctx.expires !== 0 ? ctx.timestamp + ctx.expires * 1000 : 0
            }

            if (status instanceof Error) {
                result.level = "error"
                result.text = status.toString()
                if (status.stack !== undefined) {
                    result.details = status.stack.toString()
                }

            } else if (typeof status === "string") {
                if (status.startsWith("I>")) {
                    result.level = "info"
                    result.text = status.substr(2)
                } else if (status.startsWith("W>")) {
                    result.level = "warning"
                    result.text = status.substr(2)
                } else if (status.startsWith("E>")) {
                    result.level = "error"
                    result.text = status.substr(2)
                } else if (status.startsWith("P>")) {
                    result.level = "primary"
                    result.text = status.substr(2)
                } else if (status.startsWith("S>")) {
                    result.level = "success"
                    result.text = status.substr(2)
                } else if (status.startsWith("%>")) {
                    result.level = "progress"
                    result.text = status.substr(2)
                } else if (status.startsWith("L>")) {
                    result.level = "light"
                    result.text = status.substr(2)
                } else if (status.startsWith("D>")) {
                    result.level = "dark"
                    result.text = status.substr(2)
                } else {
                    result.level = "secondary"
                    result.text = status
                }

            } else if (status.hasOwnProperty("responseJSON") && status.responseJSON !== null &&
                status.responseJSON.hasOwnProperty("message")) {

                /* JSON error from backend. */
                result.level = "error"
                result.text = status.responseJSON.message
                if (status.responseJSON.hasOwnProperty("fullText")) {
                    result.details = status.responseJSON.fullText
                }

            } else if (status.hasOwnProperty("status")) {
                /* jQuery XHR error. */
                result.level = "error"
                result.text = status.status.toString()
                if (status.hasOwnProperty("statusText")) {
                    result.text += " " + status.statusText
                }

            } else if (status.hasOwnProperty("text")) {
                /* Generic status object. */
                result.text = status.text
                result.isHtml = status.isHtml
                if (status.hasOwnProperty("details")) {
                    result.details = status.details
                }
                if (status.hasOwnProperty("level")) {
                    result.level = status.level
                } else {
                    result.alertClass = "alert-secondary"
                }
                if (status.hasOwnProperty("title")) {
                    result.title = status.title
                }

            } else {
                console.warn("Unrecognized status object", status)
                return null
            }

            result.id = this.curId++
            result.count = 1

            return result
        },

        _GetTextColor(level) {
            if (level === "info") {
                return "cyan-10"
            } else if (level === "warn" || level === "warning") {
                return "deep-orange-10"
            } else if (level === "error" || level === "danger") {
                return "blue-grey-1"
            } else if (level === "primary") {
                return "blue-grey-1"
            } else if (level === "success") {
                return "blue-grey-1"
            } else if (level === "secondary" || level === "progress") {
                return "blue-grey-10"
            } else if (level === "light") {
                return "blue-grey-8"
            } else if (level === "dark") {
                return "blue-grey-1"
            } else {
                console.warn("Unrecognized alert class", level)
                return "blue-grey-10"
            }
        },

        _GetBgColor(level) {
            if (level === "info") {
                return "info"
            } else if (level === "warn" || level === "warning") {
                return "warning"
            } else if (level === "error" || level === "danger") {
                return "negative"
            } else if (level === "primary") {
                return "primary"
            } else if (level === "success") {
                return "positive"
            } else if (level === "secondary" || level === "progress") {
                return "blue-grey-2"
            } else if (level === "light") {
                return "light"
            } else if (level === "dark") {
                return "dark"
            } else {
                console.warn("Unrecognized alert class", level)
                return "blue-grey-2"
            }
        },

        _GetIconName(level) {
            if (level === "info") {
                return "info"
            } else if (level === "warn" || level === "warning") {
                return "warning"
            } else if (level === "error" || level === "danger") {
                return "error"
            } else if (level === "primary") {
                return null
            } else if (level === "success") {
                return "check_circle"
            } else if (level === "secondary" || level === "progress") {
                return null
            } else if (level === "light") {
                return null
            } else if (level === "dark") {
                return null
            } else {
                console.warn("Unrecognized alert class", level)
                return null
            }
        },

        _OnDismiss(id) {
            this._RemoveItem(id)
            if (this.curExpireItem === null) {
                this._ScheduleExpire()
            }
        },

        _DismissAll() {
            this.items = []
            this.propItems = []
            this.isExpanded = false
            if (this.timerId !== null) {
                clearTimeout(this.timerId)
                this.timerId = null
                this.curExpireItem = null
            }
        },

        /*
         * Count success, info, warning and error levels. Null for all the rest.
         */
        _CollapsedCount(level) {
            let count = 0
            for (let idx = this.maxCollapsedCount; idx < this.items.length; idx++) {
                let item = this.items[idx]
                let itemLevel = item.level
                if (level === null) {
                    if (itemLevel !== "success" && itemLevel !== "info" &&
                        itemLevel !== "warning" && itemLevel !== "error") {
                        count += item.count
                    }
                } else if (itemLevel === level) {
                    count += item.count
                }
            }
            return count
        },

        _CommitItems() {
            this._SortItems()
            if (this.curExpireItem === null) {
                this._ScheduleExpire()
            }
        },

        _ScheduleExpire() {
            let minItem = null
            let now = new Date().getTime()
            let expired = []
            for (let item of this.items) {
                if (item.expires === 0) {
                    continue
                }
                if (item.expires <= now) {
                    expired.push(item)
                    continue
                }
                if (minItem === null || item.expires < minItem.expires) {
                    minItem = item
                }
            }
            for (let item of expired) {
                this._RemoveItem(item.id)
            }
            if (minItem !== null) {
                this.timerId = setTimeout(this._OnTimer, minItem.expires - now)
                this.curExpireItem = minItem
            }
        },

        _OnTimer() {
            this.timerId = null
            this.curExpireItem = null
            this._ScheduleExpire()
        }
    }
}

</script>

<style scoped lang="less">

.root {
    overflow: auto;

    .iconContainer {
        justify-content: start;
        padding-top: 4px;
    }

    .toast {
        position: fixed;
        right: 5%;
        top: 20%;
        z-index: 2000;
        max-width: 80%;
        max-height: 70%;
        overflow: auto;
        opacity: 0.9;
    }

    .top, .bottom {
        padding: 0.1rem 2rem;
        font-size: 0.8rem;
        min-height: auto;
    }

    .bottom {
        a {
            margin-right: 5px;
        }
    }

    .title {
        padding-left: 1rem;
        font-size: 0.9rem;
        font-weight: 600;
    }

    .count {
        border-width: 1px;
        border-style: solid;
    }

    details {
        summary {
            font-size: 0.7rem;
            cursor: pointer;
            text-decoration: underline;
        }

        .details {
            text-align: left;
            font-family: monospace;
            white-space: pre;
            overflow: auto;
        }
    }
}


</style>
