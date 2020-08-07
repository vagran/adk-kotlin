/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.marketminer.html.io.github.vagran.ask.html.test

import io.github.vagran.adk.html.HtmlDocumentBuilder
import io.github.vagran.adk.html.HtmlFilter
import io.github.vagran.adk.html.HtmlParser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HtmlDocumentBuilderTest {

    //language=HTML
    val sampleDoc = """
        <!DOCTYPE html><html lang="en" a=b c d='e'>
        <head>
            <title>Some title</title>
        </head>
        <body>
            <div title='div1'></div>
            <div title='div2' class='c1'><span><i>222</i></span></div>
            <div title='div3'><span>333</span></div>
            <div title='div4' class='c1'><span>444</span></div>
            <table>
                <!-- Test unclosed tr tags -->
                <tr><td>cell1</td><tr>
                <tr><td>cell2</td><tr>
            </table>
        </body>
        <!-- Some comment -->
        </html>
    """.trimIndent()

    @Test
    fun FilterTest()
    {
        val ft = HtmlFilter()
        ft.root.children.add(HtmlFilter.Node().also {
            it.elementName = "html"
            it.children.add(HtmlFilter.Node().also {
                it.elementName = "body"
                it.children.add(HtmlFilter.Node().also {
                    it.elementName = "div"
                    it.classes = ArrayList<String>().also { it.add("c1") }
                    it.extractInfo = ArrayList<HtmlFilter.ExtractInfo>().also {
                        it.add(HtmlFilter.ExtractInfo().also {
                            it.attrName = "title"
                            it.tagName = "titleTag"
                        })
                    }
                })
            })
        })
        ft.SetParents()
        ft.ReassignIds()

        val docBuilder = HtmlDocumentBuilder(ft)
        val parser = HtmlParser(docBuilder::PushToken, encoding = null)
        parser.FeedChars(sampleDoc)
        parser.Finish()
        val doc = docBuilder.Build()

        assertEquals(1, doc.root.GetElement("html")!!.children.size)

        val body = doc.root.GetElement("html")!!.GetElement("body")!!
        assertEquals(2, body.children.size)
        val div2 = body.GetElement("div", 0)!!
        val div4 = body.GetElement("div", 1)!!
        assertEquals("div2", div2.GetAttribute("title")!!.value)
        assertEquals("div4", div4.GetAttribute("title")!!.value)
        assertEquals("222", div2.GetElement("span")!!.GetElement("i")!!.InnerText())
        assertEquals("444", div4.GetElement("span")!!.InnerText())

        val result = ft.Apply(doc)
        assertEquals("div2", result.nodes["titleTag"]!![0].text)
        assertEquals("div4", result.nodes["titleTag"]!![1].text)
    }

    @Test
    fun UnclosedTableRows()
    {
        val docBuilder = HtmlDocumentBuilder()
        val parser = HtmlParser(docBuilder::PushToken, encoding = null)
        parser.FeedChars(sampleDoc)
        parser.Finish()
        val doc = docBuilder.Build()
        val body = doc.root.GetElement("html")!!.GetElement("body")!!

        val tbody = body.GetElement("table")!!.GetElement("tbody")!!
        assertEquals(tbody.GetElement("tr", 0)!!.GetElement("td", 0)!!.InnerText(), "cell1")
        assertEquals(tbody.GetElement("tr", 1)!!.InnerText(), "")
        assertEquals(tbody.GetElement("tr", 2)!!.GetElement("td", 0)!!.InnerText(), "cell2")
        assertEquals(tbody.GetElement("tr", 3)!!.InnerText(), "")
    }
}
