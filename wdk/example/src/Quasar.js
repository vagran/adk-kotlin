/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */
import "quasar/dist/quasar.css"
import "@quasar/extras/material-icons/material-icons.css"

import Quasar, {
    ClosePopup,

    Notify,

    QBadge,
    QBanner,
    QBar,
    QBtn,
    QBtnDropdown,
    QCard,
    QCardSection,
    QCheckbox,
    QDialog,
    QDrawer,
    QFooter,
    QHeader,
    QIcon,
    QInput,
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
    QSelect,
    QSeparator,
    QSpace,
    QSpinner,
    QToggle,
    QToolbar,
    QToolbarTitle,
    QTooltip,
} from "quasar"


export default [Quasar, {
    config: {
        notify: { /* Notify defaults */}
    },
    components: {
        QBadge,
        QBanner,
        QBar,
        QBtn,
        QBtnDropdown,
        QCard,
        QCardSection,
        QCheckbox,
        QDialog,
        QDrawer,
        QFooter,
        QHeader,
        QIcon,
        QInput,
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
        QSelect,
        QSeparator,
        QSpace,
        QSpinner,
        QToggle,
        QToolbar,
        QToolbarTitle,
        QTooltip,
    },
    directives: {
        ClosePopup
    },
    plugins: {
        Notify
    }
}]
