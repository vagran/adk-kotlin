goog.provide("wdk.components.nav_bar");


(function(app) {

    // language=HTML
    let tpl = `
<nav class="navbar navbar-expand-md navbar-dark bg-dark fixed-top">
    <a class="navbar-brand" :href="brandUrl">{{brand}}</a>
    <button class="navbar-toggler" type="button" data-toggle="collapse" 
          data-target="#navbarsExampleDefault" aria-controls="navbarsExampleDefault" 
          aria-expanded="false" aria-label="Toggle navigation">
        <span class="navbar-toggler-icon"></span>
    </button>
    
    <div class="collapse navbar-collapse" id="navbarsExampleDefault">
        <ul class="navbar-nav mr-auto">
            <template v-for="item in items">
                <li class="nav-item" :class="GetItemClasses(item)">
                    <a v-if="!IsDropdown(item)" class="nav-link" :href="item.url">{{item.name}}</a>
                    <template v-else>
                        <a class="nav-link dropdown-toggle" href="#" :id="NextDropdownId()" 
                        data-toggle="dropdown" aria-haspopup="true" 
                        aria-expanded="false" :class="GetItemClasses(item)">{{item.name}}</a>
                        <div class="dropdown-menu" :aria-labelledby="CurDropdownId()">
                            <template v-for="subItem in item.children">
                                <div v-if="subItem.type == 'separator'" class="dropdown-divider"></div>
                                <h6 v-else-if="subItem.type == 'header'" 
                                class="dropdown-header">{{subItem.name}}</h6>
                                <a v-else class="dropdown-item" :class="GetItemClasses(subItem)" 
                                :href="subItem.url">{{subItem.name}}</a>
                            </template>
                        </div>
                    </template>
                </li>
            </template>
        </ul>
        <slot></slot>
    </div>
</nav>
`;

    Vue.component("nav-bar", {
        template: tpl,
        /*
         * items: Each item is an object with the following fields:
         *  name: Display name.
         *  url: URL of the item action.
         *  isDisabled: Optional disabled flag.
         *  children: Children items for drop-down item.
         *  type: Optional "header" or "separator" for drop-down child item.
         */
        props: ["brand", "brandUrl", "items", "activeUrl"],
        created: function () {
            this.dropdownIndex = 0;
        },
        methods: {
            GetItemClasses: function (item) {
                let cls = {};
                if (this.activeUrl !== undefined && item.url === this.activeUrl) {
                    cls.active = true;
                }
                if (item.isDisabled) {
                    cls.disabled = true;
                }
                if (item.children !== undefined) {
                    cls.dropdown = true;
                    /* Make dropdown active if any of its child is active. */
                    for (let subItem of item.children) {
                        if (this.activeUrl !== undefined && subItem.url === this.activeUrl) {
                            cls.active = true;
                            break;
                        }
                    }
                }
                return cls;
            },

            /** @return {boolean} True if the item is dropdown. */
            IsDropdown: function(item) {
                return item.children !== undefined;
            },

            /** @return {String} Tag ID to use for next dropdown. */
            NextDropdownId: function () {
                return "dropdown_" + (++this.dropdownIndex);
            },

            /** @return {String} Tag ID to use for current dropdown. */
            CurDropdownId: function () {
                return "dropdown_" + this.dropdownIndex;
            }
        }
    });

})(window.app || (window.app = {}));
