/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */
import "bootstrap";
import "bootstrap/dist/css/bootstrap.min.css";

import Vue from "vue";
import App from "./App.vue";

import FontAwesomeIcon from "./FontIcons";

Vue.component("fa-icon", FontAwesomeIcon)

new Vue({
    el: "#app",
    render: h => h(App)
});
