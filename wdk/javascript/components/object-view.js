goog.provide("wdk.components.object_view");
/* Dumps arbitrary Javascript object. Mostly for debug purposes. */

(function(app) {

    // language=HTML
    let tpl = `        
<div :class="{'wdk_object-view_container': true, 'wdk_object-view_container_root': isRoot}">
    <div :class="GetValueTagClass()">
        <span class="type-label-outer"><span :class="GetTypeLabelClass()">{{GetTypeLabel()}}</span></span>
        <span v-if="name !== null" class="name">{{name}}:</span>
        <span v-if="HasSingleValue()" class="single-value">{{GetSingleValue()}}</span>
        <span v-if="!HasSingleValue()" class="elements-count" @click="OnExpandToggle()">({{GetElementsCount()}} elements)</span>
        <div v-if="!HasSingleValue()" class="collection-values" 
             :style="{display: isExpandedCur ? 'block' : 'none'}">
            <object-view v-for="(value, key) in object" :object="value" :isRoot="false" 
                         :name="key" :key="key" :isExpanded="isExpanded"></object-view>
        </div>
    </div>
</div>
`;
    Vue.component("object-view", {
        name: "object-view",
        template: tpl,
        props: {
            object: {
                required: true
            },
            isRoot: {
                default: true
            },
            name: {
                default: null
            },
            isExpanded: {
                default: true
            }
        },

        data: function() {
            return {
                someVar: [],
                isExpandedCur: this.isExpanded
            }
        },

        methods: {
            GetValueTagClass: function() {
                let classes = {value: true};
                classes["value-" + this.GetType(this.object)] = true;
                return classes
            },

            /**
             * @return {string} Name for type of the specified object.
             */
            GetType: function(obj) {
                let typeStr = typeof obj;
                if (this.object === null) {
                    return "null";
                } else if (typeStr === "number" || typeStr === "boolean" || typeStr === "string") {
                    return typeStr;
                } else if (typeStr === "object") {
                    if (Array.isArray(this.object)) {
                        return "array";
                    } else {
                        return "object";
                    }
                }
                return "unknown";
            },

            /**
             * @return {boolean} True if single value can be rendered.
             */
            HasSingleValue: function () {
                let type = this.GetType(this.object);
                return type === "null" || type === "number" || type === "boolean" ||
                       type === "string";
            },

            /**
             * @return {string} String representation of single value.
             */
            GetSingleValue: function () {
                let type = this.GetType(this.object);
                if (type === "string") {
                    return '"' + this.object + '"';
                } else if (type === "null") {
                    return "null";
                }
                return this.object;
            },

            OnExpandToggle: function () {
                this.isExpandedCur = !this.isExpandedCur;
            },

            GetExpandButtonClass: function () {
                let result = {};
                if (this.isExpandedCur) {
                    result.expanded = true;
                } else {
                    result.collapsed = true;
                }
                return result;
            },

            /**
             * @return {string} Content text for type label.
             */
            GetTypeLabel: function () {
                let type = this.GetType(this.object);
                switch (type) {
                    case "null":
                        /* Non-breaking space. */
                        return "\u00a0\u00a0\u00a0";
                    case "number":
                        return "123";
                    case "string":
                        return "ABC";
                    case "boolean":
                        /* Checkbox symbol. */
                        return this.object ? "\u2611" : "\u2610";
                    case "array":
                        return "[]";
                    case "object":
                        return "{}";
                }
                return "U";
            },

            GetTypeLabelClass: function () {
                let classes = {"type-label": true};
                classes["type-label-" + this.GetType(this.object)] = true;
                return classes
            },

            /**
             * @return {number} Number of child elements for collection type.
             */
            GetElementsCount: function () {
                return Object.keys(this.object).length;
            }
        }
    });

})(window.app || (window.app = {}));
