/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

import Vue from "vue"
import WdkEditableField from "./components/EditableField"
import WdkEditableProperties from "./components/EditableProperties"
import WdkEntitiesList from "./components/EntitiesList"
import WdkMessageBox from "./components/MessageBox"
import WdkObjectView from "./components/ObjectView"
import WdkStatusView from "./components/StatusView"
import WdkHtmlFilter from "./components/html/HtmlFilter"
import WdkHtmlFilterEditor from "./components/html/HtmlFilterEditor"

const components = [
    WdkEditableField,
    WdkEditableProperties,
    WdkEntitiesList,
    WdkMessageBox,
    WdkObjectView,
    WdkStatusView,
    WdkHtmlFilter,
    WdkHtmlFilterEditor
]

function RegisterComponents(components) {
    for (let comp of components) {
        Vue.component(comp.name, comp)
    }
}

export default {
    install() {
        RegisterComponents(components)
    }
}
