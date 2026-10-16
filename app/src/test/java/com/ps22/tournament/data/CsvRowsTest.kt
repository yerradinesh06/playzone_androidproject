package com.ps22.tournament.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvRowsTest {
    @Test fun parsesQuotedCommasQuotesAndNewlines() {
        assertEquals(
            listOf(listOf("name", "note"), listOf("Harbor, City", "He said \"go\""), listOf("Northline", "two\nlines")),
            CsvRows.parse("name,note\r\n\"Harbor, City\",\"He said \"\"go\"\"\"\r\nNorthline,\"two\nlines\""),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnclosedQuotes() { CsvRows.parse("team,notes\nclub,\"incomplete") }
}
