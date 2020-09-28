<!--
  - This file is part of ADK project.
  - Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
  - See LICENSE file for full license details.
  -->
<template>
<q-page padding>
    <q-card>
        <q-card-section>
            Error:
            <WdkStatusView status="E>Sample status"/>
            Warning:
            <WdkStatusView status="W>Sample status"/>
            Info:
            <WdkStatusView status="I>Sample status"/>
            Success:
            <WdkStatusView status="S>Sample status"/>
            Progress:
            <WdkStatusView status="%>Sample status"/>
            Primary:
            <WdkStatusView status="P>Sample status"/>
            Light:
            <WdkStatusView status="L>Sample status"/>
            Dark:
            <WdkStatusView status="D>Sample status"/>
            Secondary:
            <WdkStatusView status="Sample status"/>

            Title and details:
            <WdkStatusView :status="titleAndDetails"/>

            Error instance:
            <WdkStatusView :status="error"/>

            Many items:
            <WdkStatusView :status="manyItems"/>

            Fetch result:
            <WdkStatusView :status="fetchResult" />

            Aggregated:
            <WdkStatusView :status="aggregated" />
        </q-card-section>
    </q-card>
</q-page>
</template>

<script>

export default {

    data() {
        return {
            titleAndDetails: {
                title: "Sample title",
                text: "Sample status text",
                level: "info",
                details: "Sample details\nLine 2\nLine3\n    Indented line"
            },
            fetchResult: "%>Fetching...",
            aggregated: {
                svAggregated: true,
                s1: "S>Success 1",
                s2: "W>Warning 2"
            }
        }
    },

    computed: {
        error() {
            try {
                // noinspection ExceptionCaughtLocallyJS
                throw new Error("Sample error")
            } catch (e) {
                return e
            }
        },

        manyItems() {
            let result = []
            for (let i = 0; i < 10; i++) {
                result.push(`I>Item ${i}`)
            }
            result.push("E>Sample error")
            result.push("P>Sample primary")
            result.push("W>Sample warning")
            return result
        }
    },

    mounted() {
        fetch("http://non-existing.site")
            .then((response) => {
                console.log("Response:", response)
                this.fetchResult = response
            })
            .catch((e) => {
                console.log("Error:", e)
                this.fetchResult = e
            })
    }
}

</script>
