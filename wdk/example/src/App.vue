<!--
  - This file is part of ADK project.
  - Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
  - See LICENSE file for full license details.
  -->
<template>
<q-layout view="hHh lpr fff">
    <q-header>
        <q-toolbar>
            <q-toolbar-title :shrink="true" >WDK example</q-toolbar-title>
            <q-btn flat stretch label="Home" to="/" />
            <q-btn flat stretch label="Page 1" to="/page1/52" />
            <q-space />
            <q-btn-dropdown flat stretch label="Dropdown">
                <q-list>
                    <q-item-label header>Header</q-item-label>
                    <q-item clickable v-close-popup to="/page2/53">
                        <q-item-section>
                            Page 2
                        </q-item-section>
                    </q-item>
                    <q-separator />
                    <q-item-label header>WDK components</q-item-label>
                    <q-item clickable v-close-popup to="/StatusViewTest">
                        <q-item-section>StatusView</q-item-section>
                    </q-item>
                    <q-item clickable v-close-popup to="/EditableFieldTest">
                        <q-item-section>EditableField</q-item-section>
                    </q-item>
                    <q-item clickable v-close-popup to="/MessageBoxTest">
                        <q-item-section>MessageBox</q-item-section>
                    </q-item>
                    <q-item clickable v-close-popup to="/ObjectViewTest">
                        <q-item-section>ObjectView</q-item-section>
                    </q-item>
                    <q-item clickable v-close-popup to="/EditablePropertiesTest">
                        <q-item-section>EditableProperties</q-item-section>
                    </q-item>
                </q-list>
            </q-btn-dropdown>
        </q-toolbar>
    </q-header>
    <q-page-container>
        <router-view />
    </q-page-container>
</q-layout>
</template>
