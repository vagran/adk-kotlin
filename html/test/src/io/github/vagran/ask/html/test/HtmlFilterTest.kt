/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.marketminer.html.io.github.vagran.ask.html.test

import io.github.vagran.adk.html.*
import io.github.vagran.adk.html.json.htmlJsonCodecsRegistry
import io.github.vagran.adk.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.opentest4j.AssertionFailedError

//language=HTML
val sampleDoc = """
    <html>
    <head>
        <title>Sample title</title>
        <meta charset="utf-8">
        <meta name="keywords" content="sample_keyword" >
        <meta name="description" content="sample_description" >
    </head>
    <body>
        <div id="div_id" class="cls1">
            <div>div_text</div>
            <a href="link1" title="link1_title">link1_text</a>
            <a href="link2" id="link2_id" class="cls2 cls3">link2_text</a>
            <a href="link3">link3_text</a>
            <div>div2_text</div>
        </div>
        <div>

        </div>
        <test-el>
            <span test-attr="test_value">test_text_я</span>
        </test-el>
    </body>
    </html>
""".trimIndent()

val sampleAnchors = listOf(
    HtmlElement(
        "a", attrs = listOf("href" to "link1",
                            "title" to "link1_title"), innerText = "link1_text"),
    HtmlElement(
        "a", attrs = listOf("href" to "link2",
                            "class" to "cls2 cls3",
                            "id" to "link2_id"), innerText = "link2_text"),
    HtmlElement(
        "a", attrs = listOf("href" to "link3"), innerText = "link3_text")
)

val json = Json(additionalRegistries = listOf(htmlJsonCodecsRegistry))

class HtmlElement(
    val elementName: String,
    val attrs: List<Pair<String, String?>>? = null,
    val innerText: String? = null,
    val children: List<HtmlElement>? = null
)

class ResultValidator(val result: HtmlFilter.Result) {

    fun IsEmpty()
    {
        assertEquals(0, result.nodes.size)
    }

    fun TagsCount(count: Int)
    {
        assertEquals(count, result.nodes.size)
    }

    fun HasOneTextTag(tagName: String, value: String)
    {
        val nodeList = result.nodes[tagName]
        assertNotNull(nodeList)
        assertEquals(1, nodeList!!.size)
        val node = nodeList.first()
        assertEquals(value, node.text)
    }

    fun HasTextTag(tagName: String, value: String)
    {
        val nodeList = result.nodes[tagName]
        assertNotNull(nodeList)
        val node = nodeList!!.first()
        assertEquals(value, node.text)
    }

    fun HasOneHtmlTag(tagName: String, expectedElement: HtmlElement)
    {
        val nodeList = result.nodes[tagName]
        assertNotNull(nodeList)
        assertEquals(1, nodeList!!.size)
        val node = nodeList.first()
        assertNotNull(node.docNode)
        MatchHtmlElement(node.docNode!!, expectedElement)
    }

    fun HasAllHtmlTags(tagName: String, expectedElements: List<HtmlElement>)
    {
        val nodeList = result.nodes[tagName]
        assertNotNull(nodeList)
        assertEquals(expectedElements.size, nodeList!!.size)
        for (node in nodeList) {
            var found = false
            for (expectedElement in expectedElements) {
                try {
                    MatchHtmlElement(node.docNode!!, expectedElement)
                    found = true
                    break
                } catch(e: AssertionFailedError) { }
            }
            if (!found) {
                fail<Unit>("Element not matched: " + json.ToJson(node))
            }
        }
    }

    private fun MatchHtmlElement(element: HtmlDocument.ElementNode,
                                 expectedElement: HtmlElement)
    {
        assertEquals(expectedElement.elementName, element.name)
        if (expectedElement.attrs != null) {
            for (expectedAttr in expectedElement.attrs) {
                val attr = element.GetAttribute(expectedAttr.first)
                assertNotNull(attr)
                assertEquals(expectedAttr.second, attr!!.value)
            }
        }
        if (expectedElement.innerText != null) {
            assertEquals(expectedElement.innerText, element.InnerText())
        }
        if (expectedElement.children != null) {
            val childrenIt = element.children.iterator()
            for (expectedChild in expectedElement.children) {
                val nextChild: HtmlDocument.ElementNode = run {
                    while (true) {
                        val el = childrenIt.next()
                        if (el is HtmlDocument.ElementNode) {
                            return@run el
                        }
                    }
                    /* Kotlin bug */
                    @Suppress("UNREACHABLE_CODE")
                    throw Error()
                }
                MatchHtmlElement(nextChild, expectedChild)
            }
        }
    }
}

fun Check(html: String, filterStr: String,
          expectedRepr: String? = null,
          xpath: String? = null,
          error: String? = null,
          validator: (ResultValidator.() -> Unit)? = null)
{
    val docBuilder = HtmlDocumentBuilder()
    val parser = HtmlParser(docBuilder::PushToken, encoding = null)
    parser.FeedChars(html)
    parser.Finish()
    val doc = docBuilder.Build()

    val test = test@{
        filterStrRaw: String ->

        val filter = try {
            HtmlFilterParser.Expression.GetFilter(HtmlFilterParser().Parse(filterStrRaw))
        } catch (e: HtmlFilterParser.ParsingError) {
            if (error != null) {
                assertTrue(e.message!!.startsWith(error))
                return@test
            } else {
                throw e
            }
        }
        if (expectedRepr == null) {
            assertEquals(filterStr, filter.toString())
        } else {
            assertEquals(expectedRepr, filter.toString())
        }
        val result = filter.Apply(doc)
        if (validator != null) {
            ResultValidator(
                result).validator()
        }

        val docBuilderFiltered = HtmlDocumentBuilder(filter)
        val parserFiltered = HtmlParser(docBuilderFiltered::PushToken, encoding = null)
        parserFiltered.FeedChars(html)
        parserFiltered.Finish()
        val docFiltered = docBuilderFiltered.Build()
        val resultFiltered = filter.Apply(docFiltered)
        if (validator != null) {
            ResultValidator(
                resultFiltered).validator()
        }
    }

    test(filterStr)
    xpath?.also(test)
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HtmlFilterTest {

    @Test
    fun SimpleTextTag()
    {
        Check(
            sampleDoc,
            "   title@~titleTag   ", "title@~titleTag",
            xpath = "{//title:~titleTag}") {
            TagsCount(1)
            HasOneTextTag("titleTag", "Sample title")
        }
    }

    @Test
    fun BadSyntaxInTag()
    {
        Check(
            sampleDoc,
            "title@~titleTag~", error = "Unexpected character: ~")
    }

    @Test
    fun BadSyntaxInXpathTag()
    {
        Check(
            sampleDoc,
            "{//title:~titleTag~}", error = "Unexpected character: ~")
    }

    @Test
    fun SimpleTag()
    {
        Check(
            sampleDoc,
            "title@titleTag", xpath = "{//title:titleTag}") {
            TagsCount(1)
            HasOneHtmlTag("titleTag",
                          HtmlElement(
                              "title", innerText = "Sample title"))
        }
    }

    @Test
    fun RootElement()
    {
        Check(
            sampleDoc,
            "title:root@titleTag", xpath = "{/title:titleTag}") {
            IsEmpty()
        }
    }

    @Test
    fun MultipleElements()
    {
        Check(
            sampleDoc,
            "a@tag", xpath = "{//a:tag}") {
            HasAllHtmlTags("tag",
                           sampleAnchors)
        }
    }

    @Test
    fun MultipleElementsInContext()
    {
        Check(
            sampleDoc,
            "body a@tag", xpath = "{//body//a:tag}") {
            HasAllHtmlTags("tag",
                           sampleAnchors)
        }
    }

    @Test
    fun MultipleElementsInContextDirect()
    {
        Check(
            sampleDoc,
            "div a@tag", xpath = "{//div//a:tag}") {
            HasAllHtmlTags("tag",
                           sampleAnchors)
        }
    }

    @Test
    fun MultipleElementsInContextDirectChild()
    {
        Check(
            sampleDoc,
            "div > a@tag", xpath = "{//div/a:tag}") {
            HasAllHtmlTags("tag",
                           sampleAnchors)
        }
    }

    @Test
    fun MultipleElementsInContextDirectChildNoMatch()
    {
        Check(
            sampleDoc,
            "body > a@tag", xpath = "{//body/a:tag}") {
            IsEmpty()
        }
    }

    @Test
    fun MultipleElementsInContextDirectChildFromRoot()
    {
        Check(
            sampleDoc,
            "html:root > body > div > a@tag", xpath = "{/html/body/div/a:tag}") {
            HasAllHtmlTags("tag",
                           sampleAnchors)
        }
    }

    @Test
    fun MultipleElementsInContextDirectChildren()
    {
        Check(
            sampleDoc,
            "body > div > a@tag", xpath = "{//body/div/a:tag}") {
            HasAllHtmlTags("tag",
                           sampleAnchors)
        }
    }

    @Test
    fun MultipleElementsInContextDirectChildrenMixed()
    {
        Check(
            sampleDoc,
            "html > body div > a@tag", xpath = "{//html/body//div/a:tag}") {
            HasAllHtmlTags("tag",
                           sampleAnchors)
        }
    }

    @Test
    fun MultipleElementsInContextChildWildcard()
    {
        Check(
            sampleDoc,
            "body > * > a@tag", xpath = "{//body/*/a:tag}") {
            HasAllHtmlTags("tag",
                           sampleAnchors)
        }
    }

    @Test
    fun MultipleElementsInContextChildWildcardNoMatch()
    {
        Check(
            sampleDoc,
            "div > * > a@tag", xpath = "{//div/*/a:tag}") {
            IsEmpty()
        }
    }

    @Test
    fun MultipleElementsInContextWildcard()
    {
        Check(
            sampleDoc,
            "body * a@tag", xpath = "{//body//*//a:tag}") {
            HasAllHtmlTags("tag",
                           sampleAnchors)
        }
    }

    @Test
    fun MultipleElementsInContextWildcardNoMatch()
    {
        Check(
            sampleDoc,
            "div * a@tag", xpath = "{//div//*//a:tag}") {
            IsEmpty()
        }
    }

    @Test
    fun IdElement()
    {
        Check(
            sampleDoc,
            "#link2_id@~tag", xpath = "{//*[@id=link2_id]:~tag}") {
            HasOneTextTag("tag", "link2_text")
        }
    }

    @Test
    fun IdElementWithName()
    {
        Check(
            sampleDoc,
            "a#link2_id@~tag", xpath = "{//a[@id=\"link2_id\"]:~tag}") {
            HasOneTextTag("tag", "link2_text")
        }
    }

    @Test
    fun IdElementWithNameNoMatch()
    {
        Check(
            sampleDoc,
            "div#link2_id@~tag", xpath = "{//div[@id=link2_id]:~tag}") {
            IsEmpty()
        }
    }

    @Test
    fun Attr()
    {
        Check(
            sampleDoc,
            "a[title]@~tag", xpath = "{//a[@title]:~tag}") {
            HasOneTextTag("tag", "link1_text")
        }
    }

    @Test
    fun AttrValue()
    {
        Check(
            sampleDoc,
            "a[title=link1_title]@~tag", "a[title=\"link1_title\"]@~tag",
            xpath = "{//a[@title=link1_title]:~tag}") {
            HasOneTextTag("tag", "link1_text")
        }
    }

    @Test
    fun HyphenatedPath()
    {
        Check(
            sampleDoc,
            "test-el > span[test-attr=\"test_value\"]@~tag",
            xpath = "{//test-el/span[@test-attr=test_value]:~tag}") {
            HasOneTextTag("tag", "test_text_я")
        }
    }

    @Test
    fun AttrQuotedValue()
    {
        Check(
            sampleDoc,
            "a[title=\"link1_title\"]@~tag",
            xpath = "{//a[@title=\"link1_title\"]:~tag}") {
            HasOneTextTag("tag", "link1_text")
        }
    }

    @Test
    fun AttrValueNoMatch()
    {
        Check(
            sampleDoc,
            "a[title=\"link1_title_aaa\"]@~tag",
            xpath = "{//a[@title=link1_title_aaa]:~tag}") {
            IsEmpty()
        }
    }

    @Test
    fun AttrTag()
    {
        Check(
            sampleDoc,
            "a[title][@titleTag:title]", xpath = "{//a[@title]/@title:titleTag}") {
            HasOneTextTag("titleTag", "link1_title")
        }
    }

    @Test
    fun MissingAttrValue()
    {
        Check(
            sampleDoc,
            "a#link2_id[@titleTag:title]",
            xpath = "{//a[@id=link2_id]/@title:titleTag}") {
            IsEmpty()
        }
    }

    @Test
    fun Classes()
    {
        Check(
            sampleDoc,
            ".cls2@~tag") {
            HasOneTextTag("tag", "link2_text")
        }
    }

    @Test
    fun ClassesSecond()
    {
        Check(
            sampleDoc,
            ".cls3@~tag") {
            HasOneTextTag("tag", "link2_text")
        }
    }

    @Test
    fun ClassesMultiple()
    {
        Check(
            sampleDoc,
            ".cls2.cls3@~tag") {
            HasOneTextTag("tag", "link2_text")
        }
    }

    @Test
    fun ClassesMultipleNoMatch()
    {
        Check(
            sampleDoc,
            ".cls2.cls4@~tag") {
            IsEmpty()
        }
    }

    @Test
    fun NthChild()
    {
        Check(
            sampleDoc,
            "#div_id a:nth-child(2)@~tag") {
            HasOneTextTag("tag", "link1_text")
        }
    }

    @Test
    fun NthOfType()
    {
        Check(
            sampleDoc,
            "#div_id a:nth-of-type(2)@~tag", xpath = "{//*[@id=div_id]//a[2]:~tag}") {
            HasOneTextTag("tag", "link2_text")
        }
    }

    @Test
    fun NthLastChild()
    {
        Check(
            sampleDoc,
            "#div_id a:nth-last-child(2)@~tag") {
            HasOneTextTag("tag", "link3_text")
        }
    }

    @Test
    fun NthLastOfType()
    {
        Check(
            sampleDoc,
            "#div_id a:nth-last-of-type(2)@~tag") {
            HasOneTextTag("tag", "link2_text")
        }
    }

    @Test
    fun AuxSelector()
    {
        Check(
            sampleDoc,
            "div > a:nth-child(3)@~tag; {/html/body/div/a[2]}",
            "html:root > body > div > a:nth-of-type(2)@~tag") {
            HasOneTextTag("tag", "link2_text")
        }
    }

    @Test
    fun AuxSelectorId()
    {
        Check(
            sampleDoc,
            "#div_id > a:nth-child(3)@~tag; {//*[@id=div_id]/a[2]}",
            "#div_id > a:nth-of-type(2)@~tag") {
            HasOneTextTag("tag", "link2_text")
        }
    }

    @Test
    fun MultipleSelectors()
    {
        Check(
            sampleDoc,
            "div > a[title]@~tag1, div > a.cls2@~tag2") {
            TagsCount(2)
            HasTextTag("tag1", "link1_text")
            HasTextTag("tag2", "link2_text")
        }
    }

    @Test
    fun MultipleSelectors2()
    {
        Check(
            sampleDoc,
            "div > a[title]@~tag1, #div_id > a.cls2@~tag2") {
            TagsCount(2)
            HasTextTag("tag1", "link1_text")
            HasTextTag("tag2", "link2_text")
        }
    }

    @Test
    fun MultipleSelectorsAttributes()
    {
        Check(
            sampleDoc,
            "meta[name=\"keywords\"][@keywordsTag:content], meta[name=\"description\"][@descTag:content]") {
            TagsCount(2)
            HasTextTag("keywordsTag", "sample_keyword")
            HasTextTag("descTag", "sample_description")
        }
    }

    @Test
    fun MultipleSelectorsTagsMerging()
    {
        Check(
            sampleDoc,
            "html > body@bodyTag > div, html > body > div@divTag",
            "html > body@bodyTag > div@divTag")
        Check(
            sampleDoc,
            "html > body > div@divTag, html > body@bodyTag > div",
            "html > body@bodyTag > div@divTag")
        Check(
            sampleDoc,
            "html > body@bodyTag > div@divTag, html > body@bodyTag > div",
            "html > body@bodyTag > div@divTag")
        Check(
            sampleDoc,
            "html > body@bodyTag > div@divTag, html > body@bodyTag > div@divTag",
            "html > body@bodyTag > div@divTag")
        Check(
            sampleDoc,
            "html > body@bodyTag, html > body > div@divTag",
            "html > body@bodyTag > div@divTag")
        Check(
            sampleDoc,
            "html > body, html > body@bodyTag > div@divTag",
            "html > body@bodyTag > div@divTag")
    }

    @Test
    fun FragmentExtraction()
    {
        Check(
            sampleDoc,
            "test-el@tag") {
            HasOneHtmlTag("tag",
                          HtmlElement(
                              "test-el",
                              children = listOf(
                                  HtmlElement(
                                      "span",
                                      attrs = listOf("test-attr" to "test_value"),
                                      innerText = "test_text_я")
                              )))
        }

        Check(
            sampleDoc,
            "html:root > body > test-el@tag") {
            HasOneHtmlTag("tag",
                          HtmlElement(
                              "test-el",
                              children = listOf(
                                  HtmlElement(
                                      "span",
                                      attrs = listOf("test-attr" to "test_value"),
                                      innerText = "test_text_я")
                              )))
        }
    }
}
