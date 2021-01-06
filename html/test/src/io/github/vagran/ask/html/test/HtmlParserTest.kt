/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.marketminer.html.io.github.vagran.ask.html.test

import io.github.vagran.adk.html.HtmlParser
import io.github.vagran.adk.html.HtmlPullParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.fail
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets


class TokenListBuilder {

    fun Tag(name: String, init: (TokenListBuilder.() -> Unit)? = null)
    {
        Tag(name, false, init)
    }

    fun TagSelfClosing(name: String, init: (TokenListBuilder.() -> Unit)? = null)
    {
        Tag(name, true, init)
    }

    private fun Tag(name: String, isSelfClosing: Boolean, init: (TokenListBuilder.() -> Unit)?)
    {
        tokens.add(HtmlParser.Token(HtmlParser.Token.Type.TAG_OPEN, name))
        if (init != null) {
            init()
        }
        tokens.add(HtmlParser.Token(
            if (isSelfClosing) HtmlParser.Token.Type.TAG_SELF_CLOSING
            else HtmlParser.Token.Type.TAG_CLOSE,
            name))
    }

    fun Attr(name: String, value: String? = null)
    {
        tokens.add(HtmlParser.Token(HtmlParser.Token.Type.ATTR_NAME, name))
        if (value != null) {
            tokens.add(HtmlParser.Token(HtmlParser.Token.Type.ATTR_VALUE, value))
        }
    }

    fun Comment()
    {
        tokens.add(HtmlParser.Token(HtmlParser.Token.Type.COMMENT, ""))
    }

    fun Text(text: String)
    {
        tokens.add(HtmlParser.Token(HtmlParser.Token.Type.TEXT, text))
    }

    fun Token(type: HtmlParser.Token.Type, value: String)
    {
        tokens.add(HtmlParser.Token(type, value))
    }

    fun Error(message: String)
    {
        errors.add(message)
    }

    val tokens = ArrayList<HtmlParser.Token>()
    val errors = ArrayList<String>()
}

fun TokenList(init: TokenListBuilder.() -> Unit): TokenListBuilder
{
    val b =
        TokenListBuilder()
    b.init()
    b.Token(HtmlParser.Token.Type.EOF, "")
    return b
}

fun Check(html: String,
          options: HtmlParser.Options = HtmlParser.Options(),
          tokens: TokenListBuilder.() -> Unit)
{
    val expectedTokens =
        TokenList(
            tokens)

    val parser = HtmlPullParser(ByteArrayInputStream(html.toByteArray(StandardCharsets.UTF_8)),
                                options = options,
                                storeErrors = true)

    val tokensIt = expectedTokens.tokens.iterator()
    for (token in parser) {
        println(token)
        if (!tokensIt.hasNext()) {
            fail("Unexpected token after expected list end: $token")
        }
        val expectedToken = tokensIt.next()
        assertEquals(expectedToken, token)
    }

    val errorsIt = expectedTokens.errors.iterator()
    for (error in parser.GetErrors()) {
        println("ERROR at <${error.lineNumber}:${error.colNumber}>: ${error.message}")
        if (!errorsIt.hasNext()) {
            fail("Unexpected error at <${error.lineNumber}:${error.colNumber}>: ${error.message}")
        }
        val expectedMessage = errorsIt.next()
        assertEquals(expectedMessage, error.message)
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HtmlParserTest {

    @Test
    fun BasicTest()
    {
        //language=HTML
        Check(
            """<!DOCTYPE html><html lang="en" a=b c d='e'>
<body>
  Some string <b>111</B> <I>222</i> 333<br/>
</body>
<!-- Some comment -->
</html>""") {
            Tag("html") {
                Attr("lang", "en")
                Attr("a", "b")
                Attr("c")
                Attr("d", "e")
                Tag("body") {
                    Text("Some string ")
                    Tag("b") { Text("111") }
                    Text(" ")
                    Tag("i") { Text("222") }
                    Text(" 333")
                    TagSelfClosing("br")
                }
                Comment()
            }
        }
    }

    @Test
    fun UnclosedTagTest()
    {
        Check(
            "<span ") {
            TagSelfClosing("span")
            Error("Incomplete tag: span")
        }
    }

    @Test
    fun CharacterReferenceTest()
    {
        Check(
            """<span a="b&amp;c&quot;d&lt;&GT;">&#65;&#x42;&#X43;</span>""") {
            Tag("span") {
                Attr("a", "b&c\"d<>")
                Text("ABC")
            }
        }
        Check(
            """<span a="&quot"></span>""") {
            Tag("span") {
                Attr("a", "&quot")
            }
        }
        Check(
            "<span>&#0;</span>") {
            Tag("span") {
                Text("\uFFFD")
                Error("Illegal code specified via numeric character reference: 0h")
            }
        }
        Check(
            "<span>&#x1;</span>") {
            Tag("span") {
                Text("\uFFFD")
                Error("Invalid code specified via numeric character reference: 1h")
            }
        }
        Check(
            "<span>&#x80;</span>") {
            Tag("span") {
                Text("\u20AC")
                Error("Illegal code specified via numeric character reference: 80h")
            }
        }
    }

    @Test
    fun EmptyTags()
    {
        //language=HTML
        Check(
            """<html>
<head>
  <metA>
  <linK>
  <meta/>
</head>
</html>""") {
            Tag("html") {
                Tag("head") {
                    TagSelfClosing("meta")
                    TagSelfClosing("link")
                    TagSelfClosing("meta")
                }
            }
        }

        Check(
            "<meta></meta>") {
            TagSelfClosing("meta")
            Error("Unmatched closing tag: meta")
        }
    }

    @Test
    fun RawTextTest()
    {
        //language=HTML
        Check(
            """
            <html>
                <head>
                    <title>Some <br> title &lt;</title>
<script>
let a = "&lt;";
let b =   42   ;
</script>
                </head>
            </html>
        """.trimIndent(), HtmlParser.Options(skipScriptText = false)) {
            Tag("html") {
                Tag("head") {
                    Tag("title") {
                        Text("Some <br> title <")
                    }
                    Tag("script") {
                        Text("""
let a = "&lt;";
let b =   42   ;
""")
                    }
                }
            }
        }
    }

    @Test
    fun ScriptSkipTest()
    {
        //language=HTML
        Check(
            """
            <html>
                <script type='text/javascript'>
                let a = 42;
                </script>
                <script>
                let b = 42;
                </script>
            </html>
        """, HtmlParser.Options(skipScriptText = true)) {
            Tag("html") {
                Tag("script") {
                    Attr("type", "text/javascript")
                }
                Tag("script")
            }
        }
    }

    @Test
    fun EofUnclosedTagsTest()
    {
        Check(
            "<html><head>") {
            Tag("html") {
                Tag("head")
            }
            Error("Misnested or unclosed tag: head")
            Error("Misnested or unclosed tag: html")
        }
    }

    @Test
    fun ProcessingInstructionTest()
    {
        Check(
            "<html><? aaa ?></html>") {
            Tag("html")
            Error("Unexpected processing instruction")
        }
    }

    @Test
    fun CdataTest()
    {
        //language=HTML
        Check(
            """
            <html><![CDATA[
            a   b  c d &lt;&amp;<br>]] ]]>
</html>
        """) {
            Tag("html") {
                Text("a b c d &lt;&amp;<br>]]")
            }
        }
    }

    @Test
    fun BogusCommentTest()
    {
        Check(
            "<!->") {
            Error("Bogus comment")
        }
        Check(
            "<!-->") {
            Error("Bogus comment")
        }
    }

    @Test
    fun XmlTest() {
        //language=HTML
        Check(
            """<?xml version="1.0" encoding="ISO-8859-1" ?>
<html lang="en" a=b c d='e'>
<body>
  Some string <b>111</B> <I>222</i> 333<br/>
  <?someProcessingInstr a b c 123 ?>
</body>
<!-- Some comment -->
</html>""",
            HtmlParser.Options(isXml = true)) {
            Tag("html") {
                Attr("lang", "en")
                Attr("a", "b")
                Attr("c")
                Attr("d", "e")
                Tag("body") {
                    Text("Some string ")
                    Tag("b") { Text("111") }
                    Text(" ")
                    Tag("i") { Text("222") }
                    Text(" 333")
                    TagSelfClosing("br")
                }
                Comment()
            }
        }
    }

    @Test
    fun XmlNoDeclTest() {
        //language=HTML
        Check(
            """
<html lang="en" a=b c d='e'>
<body>
  Some string <b>111</B> <I>222</i> 333<br/>
  <?someProcessingInstr a b c 123 ?>
</body>
<!-- Some comment -->
</html>""",
            HtmlParser.Options(isXml = true)) {
            Tag("html") {
                Attr("lang", "en")
                Attr("a", "b")
                Attr("c")
                Attr("d", "e")
                Tag("body") {
                    Text("Some string ")
                    Tag("b") { Text("111") }
                    Text(" ")
                    Tag("i") { Text("222") }
                    Text(" 333")
                    TagSelfClosing("br")
                }
                Comment()
            }
            Error("XML declaration expected")
        }
    }

    @Test
    fun TableTest()
    {
        //language=HTML
        Check(
            """
<html>
<body>
    <table>
    <caption>Caption
    <col a='b'>
    <colgroup>
    <col c='d'>
    <tr>
    <td>cell1
    <tr>
    <td>cell2
    <td>cell3
    </table>
</body>
</html>
        """) {
            Tag("html") {
                Tag("body") {
                    Tag("table") {
                        Tag("caption") {
                            Text("Caption")
                        }
                        TagSelfClosing("col") {
                            Attr("a", "b")
                        }
                        Tag("colgroup") {
                            TagSelfClosing("col") {
                                Attr("c", "d")
                            }
                        }
                        Tag("tbody") {
                            Tag("tr") {
                                Tag("td") {
                                    Text("cell1")
                                }
                            }
                            Tag("tr") {
                                Tag("td") {
                                    Text("cell2")
                                }
                                Tag("td") {
                                    Text("cell3")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun TableImpliedTags()
    {
        //language=HTML
        Check(
            """
<html>
<body>
    <table>
        <td>cell1
    </table>
</body>
</html>
        """) {
            Tag("html") {
                Tag("body") {
                    Tag("table") {
                        Tag("tbody") {
                            Tag("tr") {
                                Tag("td") {
                                    Text("cell1")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun TableImpliedTagsUnclosedCellTag()
    {
        //language=HTML
        Check(
            """
<html>
<body>
    <table>
        <td><b>cell1
        <tr><td>cell2
    </table>
</body>
</html>
        """) {
            Tag("html") {
                Tag("body") {
                    Tag("table") {
                        Tag("tbody") {
                            Tag("tr") {
                                Tag("td") {
                                    Tag("b") {
                                        Text("cell1")
                                    }
                                }
                            }
                            Tag("tr") {
                                Tag("td") {
                                    Text("cell2")
                                }
                            }
                        }
                    }
                }
            }
            Error("Misnested or unclosed tag: b")
        }
    }
}

