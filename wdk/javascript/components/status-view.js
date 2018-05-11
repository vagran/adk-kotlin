goog.provide("wdk.components.status_view");

(function(app) {

    // language=HTML
    let tpl = `
<div v-if="hasVisibleItems" class="wdk_status-view">
    <div v-for="(item, index) in items" class="alert" :class="item.alertClass" role="alert">
        <span v-if="item.inProgress" class="loader"></span>
        {{item.text}}
        <button type="button" class="close" @click="OnDismiss(index)">
            <span>&times;</span>
        </button>
        <details v-if="item.details !== null">
            <summary>Details</summary>
            <div class="details">{{item.details}}</div>
        </details>
    </div>
</div>
`;

    Vue.component("status-view", {
        template: tpl,
        props: ["status"],
        data: function() {
            return {
                hidden: []
            }
        },
        watch: {
            status: function() {
                this.hidden = [];
            }
        },
        computed: {
            items: function () {
                if (this.status === null) {
                    return null;
                }

                let GetItem = function (obj) {
                    let result = {details: null, inProgress: false};
                    if ($.type(obj) === "string") {
                        if (obj.startsWith("I>")) {
                            result.alertClass = "alert-info";
                            result.text = obj.substr(2);
                        } else if (obj.startsWith("W>")) {
                            result.alertClass = "alert-warning";
                            result.text = obj.substr(2);
                        } else if (obj.startsWith("E>")) {
                            result.alertClass = "alert-danger";
                            result.text = obj.substr(2);
                        } else if (obj.startsWith("P>")) {
                            result.alertClass = "alert-primary";
                            result.text = obj.substr(2);
                        } else if (obj.startsWith("S>")) {
                            result.alertClass = "alert-success";
                            result.text = obj.substr(2);
                        } else if (obj.startsWith("%>")) {
                            result.alertClass = "alert-secondary";
                            result.text = obj.substr(2);
                            result.inProgress = true;
                        } else {
                            result.alertClass = "alert-secondary";
                            result.text = obj;
                        }
                        return result;

                    } else if (obj.hasOwnProperty("responseJSON") && obj.responseJSON !== null &&
                               obj.responseJSON.hasOwnProperty("message")) {

                        result.alertClass = "alert-danger";
                        result.text = obj.responseJSON.message;
                        if (obj.responseJSON.hasOwnProperty("fullText")) {
                            result.details = obj.responseJSON.fullText;
                        }
                        //XXX details
                        return result;

                    } else if (obj.hasOwnProperty("status")) {
                        result.alertClass = "alert-danger";
                        result.text = obj.status.toString();
                        if (obj.hasOwnProperty("statusText")) {
                            result.text += " " + obj.statusText;
                        }
                        return result;
                    }
                    return null;
                };

                let result = [];
                if (Array.isArray(this.status)) {
                    for (let index = 0; index < this.status.length; index++) {
                        if (this.hidden.includes(index)) {
                            continue;
                        }
                        let item = this.status[index];
                        let _item = GetItem(item);
                        if (_item !== null) {
                            result.push(_item);
                        }
                    }
                } else {
                    let item = GetItem(this.status);
                    if (item !== null && !this.hidden.includes(0)) {
                        result.push(item);
                    }
                }
                return result;
            },

            hasVisibleItems: function() {
                if (this.status === null) {
                    return false;
                }
                let numItems;
                if (Array.isArray(this.status)) {
                    numItems = this.status.length;
                } else {
                    numItems = 1;
                }
                return this.hidden.length < numItems;
            }
        },
        methods: {
            OnDismiss: function(index) {
                this.hidden.push(index);
            }
        }
    });

})(window.app || (window.app = {}));
