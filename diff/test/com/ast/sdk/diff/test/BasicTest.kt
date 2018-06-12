package com.ast.sdk.diff.test

import com.ast.adk.diff.Diff
import com.ast.adk.diff.DiffCalculator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class BasicTest {

    fun StrDiff(s1: CharSequence, s2: CharSequence, diff: Diff): String
    {
        val sb = StringBuilder()
        for (e in diff) {
            when {
                e.type == DiffCalculator.DiffEntry.Type.INSERTION -> {
                    sb.append("+ ${s2.subSequence(e.idx2Start, e.idx2End)}\n")
                }
                e.type == DiffCalculator.DiffEntry.Type.DELETION -> {
                    sb.append("- ${s1.subSequence(e.idx1Start, e.idx1End)}\n")
                }
                e.type == DiffCalculator.DiffEntry.Type.COPY -> {
                    sb.append("  ${s1.subSequence(e.idx1Start, e.idx1End)}\n")
                }
            }
        }
        return sb.toString()
    }

    fun CheckDiff(s1: String, s2: String, diff: Diff, expected: String)
    {
        val diffStr = StrDiff(s1, s2, diff)
        println("===========================================")
        println(diffStr)
        assertEquals(expected, diffStr)
    }

    fun CheckDiff(s1: String, s2: String, expected: String)
    {
        val dc = DiffCalculator(DiffCalculator.StringAccessor(s1, s2))
        val diff = dc.Calculate()
        CheckDiff(s1, s2, diff, expected)
    }

    @Test
    fun BasicTest()
    {
        CheckDiff("ABCABBA", "CBABAC",
"""- AB
  C
+ B
  AB
- B
  A
+ C
""")
    }

    @Test
    fun EmptyStringsTest()
    {
        CheckDiff("", "", "")
    }

    @Test
    fun EqualStringsTest()
    {
        CheckDiff("ABCABBA", "ABCABBA", "  ABCABBA\n")
    }

    @Test
    fun FullDeletionTest()
    {
        CheckDiff("ABCABBA", "", "- ABCABBA\n")
    }

    @Test
    fun FullInsertionTest()
    {
        CheckDiff("", "ABCABBA", "+ ABCABBA\n")
    }

    @Test
    fun PrependTest()
    {
        CheckDiff("ABCABBA", "12345ABCABBA", "+ 12345\n  ABCABBA\n")
    }

    @Test
    fun AppendTest()
    {
        CheckDiff("ABCABBA", "ABCABBA12345", "  ABCABBA\n+ 12345\n")
    }

    @Test
    fun LeadingDeletionTest()
    {
        CheckDiff("12345ABCABBA", "ABCABBA", "- 12345\n  ABCABBA\n")
    }

    @Test
    fun TrailingDeletionTest()
    {
        CheckDiff("ABCABBA12345", "ABCABBA", "  ABCABBA\n- 12345\n")
    }

    @Test
    fun PrependAppendTest()
    {
        CheckDiff("ABCABBA", "12345ABCABBA67890", "+ 12345\n  ABCABBA\n+ 67890\n")
    }

    @Test
    fun LeadingTrailingDeletionTest()
    {
        CheckDiff("12345ABCABBA67890", "ABCABBA", "- 12345\n  ABCABBA\n- 67890\n")
    }
}
