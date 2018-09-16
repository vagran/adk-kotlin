goog.provide("wdk.components.object_view");
/* Dumps arbitrary Javascript object. Mostly for debug purposes. */

(function(wdk) {

    // language=HTML
    let tpl = `
<div :class="{'wdk_object-view_container': true, 'wdk_object-view_container_root': isRoot}">
    <div :class="valueTagClass">
        <span class="type-label-outer"><span :class="typeLabelClass">{{typeLabel}}</span></span>
        <span v-if="name !== null" class="name">{{name}}:</span>
        <span v-if="hasSingleValue" class="single-value">{{singleValue}}</span>
        <span v-if="!hasSingleValue" class="elements-count" @click="OnExpandToggle()">({{elementsCount}} elements)</span>
        <div v-if="!hasSingleValue && isExpandedCur" class="collection-values" >
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

        data() {
            return {
                someVar: [],
                isExpandedCur: this.isExpanded
            }
        },

        computed: {
            /**
             * @return {boolean} True if single value can be rendered.
             */
            hasSingleValue() {
                if (this.object === undefined) {
                    return true;
                }
                let type = this.GetType(this.object);
                return type === "null" || type === "number" || type === "boolean" ||
                    type === "string" || type === "function";
            },

            valueTagClass() {
                let classes = {value: true};
                classes["value-" + this.GetType(this.object)] = true;
                return classes
            },

            /**
             * @return {string} Content text for type label.
             */
            typeLabel() {
                if (this.object === undefined) {
                    return "U";
                }
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
                    case "function":
                        return "F";
                }
                return "?";
            },

            typeLabelClass() {
                let classes = {"type-label": true};
                classes["type-label-" + this.GetType(this.object)] = true;
                return classes
            },

            /**
             * @return {number} Number of child elements for collection type.
             */
            elementsCount() {
                return Object.keys(this.object).length;
            },

            /**
             * @return {string} String representation of single value.
             */
            singleValue() {
                if (this.object === undefined) {
                    return "undefined";
                }
                let type = this.GetType(this.object);
                if (type === "string") {
                    return '"' + this.object + '"';
                } else if (type === "function") {
                    return this.object.name;
                } else if (type === "null") {
                    return "null";
                }
                return this.object;
            },
        },

        methods: {
            /**
             * @return {string} Name for type of the specified object.
             */
            GetType(obj) {
                if (obj === undefined) {
                    return "undefined";
                }
                let typeStr = typeof obj;
                if (this.object === null) {
                    return "null";
                } else if (typeStr === "number" || typeStr === "boolean" || typeStr === "string" ||
                           typeStr === "function") {
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

            OnExpandToggle() {
                this.isExpandedCur = !this.isExpandedCur;
            },
        }
    });

})(window.wdk || (window.wdk = {}));
