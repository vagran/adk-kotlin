/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

module.exports = {
    chainWebpack: config => {
        config.plugins.delete("preload")
    },
    devServer: {
        proxy: {
            "^/api/.*": {
                target: "http://127.0.0.1:9090",
                changeOrigin: true
            }
        }
    }
}
