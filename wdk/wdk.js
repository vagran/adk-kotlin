/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

import Vue from "vue"
import WdkEditableField from "./components/EditableField"
import WdkEditableProperties from "./components/EditableProperties"
import WdkEntitiesList from "./components/EntitiesList"
import WdkHtmlElement from "./components/html/HtmlElement"
import WdkHtmlFilter from "./components/html/HtmlFilter"
import WdkHtmlFilterEditor from "./components/html/HtmlFilterEditor"
import WdkHtmlFilterResult from "./components/html/HtmlFilterResult"
import WdkHtmlViewer from "./components/html/HtmlViewer"
import WdkMessageBox from "./components/MessageBox"
import WdkObjectView from "./components/ObjectView"
import WdkStatusView from "./components/StatusView"

const components = [
    WdkEditableField,
    WdkEditableProperties,
    WdkEntitiesList,
    WdkHtmlElement,
    WdkHtmlFilter,
    WdkHtmlFilterEditor,
    WdkHtmlFilterResult,
    WdkHtmlViewer,
    WdkMessageBox,
    WdkObjectView,
    WdkStatusView,
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
