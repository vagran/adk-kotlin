/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

export default {
    namespaced: true,
    state: {
        counter: 0
    },
    mutations: {
        increment(state)
        {
            state.counter++
        }
    }
}
