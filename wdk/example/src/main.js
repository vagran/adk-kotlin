/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */
import "bootstrap";
import "bootstrap/dist/css/bootstrap.min.css";

import Vue from "vue";
import VueRouter from "vue-router";
import App from "./App.vue";
import "../assets/styles/global.less";

import FontAwesomeIcon from "./FontIcons";

Vue.component("fa-icon", FontAwesomeIcon)

Vue.use(VueRouter);
import routes from "./Routes";
const router = new VueRouter({ routes });

new Vue({
    el: "#app",
    router,
    render: h => h(App)
});
