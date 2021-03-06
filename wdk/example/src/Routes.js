/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

import PageNotFound from "@/pages/PageNotFound"
import Page1 from "@/pages/Page1"
import Page2 from "@/pages/Page2"
import LandingPage from "@/pages/LandingPage"
import StatusViewTest from "@/pages/StatusViewTest"
import EditableFieldTest from "@/pages/EditableFieldTest";
import MessageBoxTest from "@/pages/MessageBoxTest";
import ObjectViewTest from "@/pages/ObjectViewTest";
import EditablePropertiesTest from "@/pages/EditablePropertiesTest";

export default [
    { path: "/", redirect: "/home" },
    { path: "/home", component: LandingPage },
    { path: "/page1/:id", component: Page1 },
    { path: "/page2/:id", component: Page2 },
    { path: "/StatusViewTest", component: StatusViewTest },
    { path: "/EditableFieldTest", component: EditableFieldTest },
    { path: "/MessageBoxTest", component: MessageBoxTest },
    { path: "/ObjectViewTest", component: ObjectViewTest },
    { path: "/EditablePropertiesTest", component: EditablePropertiesTest },
    { path: "*", component: PageNotFound }
]
