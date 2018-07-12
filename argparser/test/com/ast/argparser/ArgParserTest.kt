package com.ast.argparser

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.nio.file.Path

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArgParserTest {

    class Options1 {
        @CliOption("a")
        var a: String? = null
        var beta: Boolean = false
        @CliOption("c")
        var cSet: Boolean = false
        var int: Int = 0
        @CliOption(required = true)
        var float: Float = 0.0f
        var double: Double = 0.0
        @CliOption("l")
        var list = ArrayList<String>()
    }

    @Test
    fun BasicTest() {
        val parser = ArgParser(
            "-a aa bbb --beta -c -def cccc ddd eee --int 42 --float 0.5 -l a -l b --double 0.25")
        val opt = parser.MapOptions<Options1>()
        assertEquals("aa", opt.a)
        assertTrue(opt.beta)
        assertTrue(opt.cSet)
        assertEquals(42, opt.int)
        assertEquals(0.5f, opt.float)
        assertEquals(0.25, opt.double)

        assertEquals(2, opt.list.size)
        assertEquals("a", opt.list[0])
        assertEquals("b", opt.list[1])
    }

    @Test
    fun MissingArgument()
    {
        val parser = ArgParser("-a --float 0.5")
        assertThrows<UsageError> {
            parser.MapOptions<Options1>()
        }
    }

    class Options2 {
        @CliOption(required = true)
        var a: Int = 0
    }

    @Test
    fun RequiredMissing() {
        val parser = ArgParser("--b")
        assertThrows<UsageError> {
            parser.MapOptions<Options2>()
        }
    }

    class Options3 {
        @CliOption("a", count = true)
        var a: Int = 0
        @CliOption("b", count = true)
        var b: Int = 0
    }

    @Test
    fun CountTest() {
        val parser = ArgParser("-aaa")
        val opt = parser.MapOptions<Options3>()
        assertEquals(3, opt.a)
        assertEquals(0, opt.b)
    }

    class Options4 {
        var a = false
        var b = false
    }

    @Test
    fun FlagTest() {
        val parser = ArgParser("--a")
        val opt = parser.MapOptions<Options4>()
        assertTrue(opt.a)
        assertFalse(opt.b)
    }

    class Options5 {
        var a: Int? = null
        var b: Float? = null
        var c: Double? = null
    }

    @Test
    fun NullableTest() {
        val parser = ArgParser("--a 42 --b 0.5 --c 0.25 a b c")
        val opt = parser.MapOptions<Options5>()
        assertEquals(42, opt.a)
        assertEquals(0.5f, opt.b)
        assertEquals(0.25, opt.c)
        val args = parser.GetPositionalArguments(3)
        assertEquals(3, args.size)
        assertEquals("a", args[0])
        assertEquals("b", args[1])
        assertEquals("c", args[2])
        parser.Finalize()
    }

    @Test
    fun InvalidIntegerTest() {
        val parser = ArgParser("--a aaa")
        assertThrows<UsageError> {
            parser.MapOptions<Options5>()
        }
    }

    class Options6 {
        @CliOption("a", "longName", description = "Some option", argName = "VALUE")
        var a: Int = 0

        @CliOption("b", "beta", description = "Another option", required = true)
        var b: Boolean = false

        var delta: Boolean = false
    }

    @Test
    fun HelpTest() {
        val parser = ArgParser("")
        val text = parser.FormatHelp {
            Text("Usage: executable <options>")
            Options<Options6>()
        }
        println(text)
        //XXX
    }

    class Options7 {
        @CliOption(booleanArg = true)
        var a: Boolean = false
    }

    @Test
    fun BooleanArgTest()
    {
        run {
            val parser = ArgParser("--a")
            assertThrows<UsageError> {
                parser.MapOptions<Options7>()
            }
        }
        run {
            val parser = ArgParser("--a 42")
            assertThrows<UsageError> {
                parser.MapOptions<Options7>()
            }
        }
        run {
            val parser = ArgParser("--a false")
            val opt = parser.MapOptions<Options7>()
            assertFalse(opt.a)
        }
        run {
            val parser = ArgParser("--a true")
            val opt = parser.MapOptions<Options7>()
            assertTrue(opt.a)
        }
        run {
            val parser = ArgParser("--a off")
            val opt = parser.MapOptions<Options7>()
            assertFalse(opt.a)
        }
        run {
            val parser = ArgParser("--a on")
            val opt = parser.MapOptions<Options7>()
            assertTrue(opt.a)
        }
        run {
            val parser = ArgParser("--a no")
            val opt = parser.MapOptions<Options7>()
            assertFalse(opt.a)
        }
        run {
            val parser = ArgParser("--a yes")
            val opt = parser.MapOptions<Options7>()
            assertTrue(opt.a)
        }
        run {
            val parser = ArgParser("--a 0")
            val opt = parser.MapOptions<Options7>()
            assertFalse(opt.a)
        }
        run {
            val parser = ArgParser("--a 1")
            val opt = parser.MapOptions<Options7>()
            assertTrue(opt.a)
        }
    }

    @Test
    fun UnconsumedArgumentTest()
    {
        val parser = ArgParser("--a on bbbb")
        parser.MapOptions<Options7>()
        assertThrows<UsageError> {
            parser.Finalize()
        }
    }

    @Test
    fun UnconsumedOptionTest()
    {
        val parser = ArgParser("--a on --b")
        parser.MapOptions<Options7>()
        assertThrows<UsageError> {
            parser.Finalize()
        }
    }

    class Options8 {
        @CliOption("aaa")
        var aaa: Int = 0
    }

    @Test
    fun InvalidShortOptionTest()
    {
        val parser = ArgParser("--aaa 42")
        assertThrows<Error> {
            parser.MapOptions<Options8>()
        }
    }

    class Options9 {
        var a: Options8? = null
    }

    @Test
    fun UnsupportedTypeTest()
    {
        val parser = ArgParser("--a 42")
        assertThrows<Error> {
            parser.MapOptions<Options9>()
        }
    }

    class Options10 {
        var path: Path? = null
    }

    @Test
    fun PathTest()
    {
        val parser = ArgParser("--path /a/b/c")
        val opt = parser.MapOptions<Options10>()
        assertEquals("/a/b/c", opt.path.toString())
    }

    class Options11 {
        lateinit var a: String
    }

    @Test
    fun LateinitTest()
    {
        val parser = ArgParser("--a abc")
        val opt = parser.MapOptions<Options11>()
        assertEquals("abc", opt.a)
    }
}
