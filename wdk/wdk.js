/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

import Vue from "vue"
import WdkStatusView from "./components/StatusView"
import WdkEditableField from "./components/EditableField"
import WdkMessageBox from "./components/MessageBox"

const components = [
    WdkStatusView,
    WdkEditableField,
    WdkMessageBox
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
