<!--
  - This file is part of ADK project.
  - Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
  - See LICENSE file for full license details.
  -->

<template>
<wdk-html-element v-if="data !== null" :element="data" :uriResolver="uriResolver"
                  :allowSel="allowSel" />
</template>

<script>
import WdkHtmlElement from "./HtmlElement"

export default {
    name: "WdkHtmlViewer",
    
    components: {WdkHtmlElement},
    
    props: {
        "data": {
            default: null
        },
        "uriResolver": {
            type: Function,
            default: path => path
        },
        "allowSel": {
            default: false
        }
    },

    methods: {
        GetSelected() {
            let result = []
            if (this.data === null) {
                return result
            }
            this._GetSelected(this.data, result)
            return result
        },

        _GetSelected(element, result) {
            if (element.isSelected) {
                result.push(element.id)
            }
            if ("attrs" in element) {
                for (let attr of element.attrs) {
                    if (attr.isSelected) {
                        result.push(attr.id)
                    }
                }
            }
            if ("children" in element) {
                for (let child of element.children) {
                    this._GetSelected(child, result)
                }
            }
        },

        ResetSelected() {
            if (this.data !== null) {
                this._ResetSelected(this.data)
            }
        },

        _ResetSelected(element) {
            if (element.isSelected) {
                element.isSelected = false
            }
            if ("attrs" in element) {
                for (let attr of element.attrs) {
                    if (attr.isSelected) {
                        attr.isSelected = false
                    }
                }
            }
            if ("children" in element) {
                for (let child of element.children) {
                    this._ResetSelected(child)
                }
            }
        }
    }
}
</script>