package com.ast.sdk.diff.test

import com.ast.adk.diff.DiffCalculator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class BasicTest {

    @Test
    fun Basic()
    {
        val dc = DiffCalculator()
    }

}
