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

    @Test
    fun Basic()
    {
        val s1 = "ABCABBA"
        val s2 = "CBABAC"
        val dc = DiffCalculator(DiffCalculator.StringAccessor(s1, s2))
        val diff = dc.Calculate()
        CheckDiff(s1, s2, diff,
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
    fun EqualStrings()
    {
        val s1 = "ABCABBA"
        val s2 = "ABCABBA"
        val dc = DiffCalculator(DiffCalculator.StringAccessor(s1, s2))
        val diff = dc.Calculate()
        CheckDiff(s1, s2, diff, "  ABCABBA\n")
    }

    @Test
    fun FullDeletion()
    {
        val s1 = "ABCABBA"
        val s2 = ""
        val dc = DiffCalculator(DiffCalculator.StringAccessor(s1, s2))
        val diff = dc.Calculate()
        CheckDiff(s1, s2, diff, "- ABCABBA\n")
    }

    @Test
    fun FullInsertion()
    {
        val s1 = ""
        val s2 = "ABCABBA"
        val dc = DiffCalculator(DiffCalculator.StringAccessor(s1, s2))
        val diff = dc.Calculate()
        CheckDiff(s1, s2, diff, "+ ABCABBA\n")
    }

    //XXX check equal

}
