/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */
import Vue from "vue"
import VueRouter from "vue-router"
import App from "@/App"
import "@/assets/styles/global.less"

import Vuex from "vuex"
Vue.use(Vuex)

import storeConfig from "@/store/index"
const store = new Vuex.Store(storeConfig)

Vue.use(VueRouter)
import routes from "@/Routes"
const router = new VueRouter({ routes })

import quasarConfig from "@/Quasar"
Vue.use(...quasarConfig)

import wdk from "wdk/wdk"
Vue.use(wdk)

new Vue({
    el: "#app",
    router,
    store,
    render: h => h(App)
})
