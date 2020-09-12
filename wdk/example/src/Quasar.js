/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */
import "quasar/dist/quasar.css"
import "@quasar/extras/material-icons/material-icons.css"

import Quasar, {
    ClosePopup,

    Notify,

    QBtn,
    QBtnDropdown,
    QCard,
    QCardSection,
    QDialog,
    QDrawer,
    QFooter,
    QHeader,
    QIcon,
    QItem,
    QItemLabel,
    QItemSection,
    QLayout,
    QList,
    QMenu,
    QPage,
    QPageContainer,
    QPageScroller,
    QPageSticky,
    QSeparator,
    QSpace,
    QSpinner,
    QToolbar,
    QToolbarTitle,
} from "quasar"


export default [Quasar, {
    config: {
        notify: { /* Notify defaults */}
    },
    components: {
        QBtn,
        QBtnDropdown,
        QCard,
        QCardSection,
        QDialog,
        QDrawer,
        QFooter,
        QHeader,
        QIcon,
        QItem,
        QItemLabel,
        QItemSection,
        QLayout,
        QList,
        QMenu,
        QPage,
        QPageContainer,
        QPageScroller,
        QPageSticky,
        QSeparator,
        QSpace,
        QSpinner,
        QToolbar,
        QToolbarTitle,
    },
    directives: {
        ClosePopup
    },
    plugins: {
        Notify
    }
}]
