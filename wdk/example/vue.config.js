/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

module.exports = {
    // configureWebpack: {
    //     module: {
    //         rules: [{
    //             test: /\.js$/,
    //             loader: 'babel-loader',
    //             exclude: file => (/node_modules/.test(file) && !/\.vue\.js/.test(file))
    //         }
    //         ]
    //     }
    // },
    chainWebpack: config => {
        config.plugins.delete("preload")
    },
    // transpileDependencies: [
    //     "wdk"
    // ],

}
