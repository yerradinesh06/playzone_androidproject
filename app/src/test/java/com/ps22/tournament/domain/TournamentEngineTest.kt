package com.ps22.tournament.domain

import com.ps22.tournament.data.TeamEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TournamentEngineTest {
    private fun teams(count: Int) = (1..count).map {
        TeamEntity("T$it", "TOUR", "Team $it", "T$it", "#005F5B", it)
    }

    private fun generate(count: Int, format: String) =
        TournamentEngine.generate("TOUR", teams(count), format, 1_800_000_000_000L, "Arena")

    @Test
    fun roundRobinCountsFor4_5_6Teams() {
        // 4 teams: N*(N-1)/2 = 6 games
        val rr4 = generate(4, TournamentEngine.ROUND_ROBIN)
        assertEquals(6, rr4.size)
        assertEquals(6, rr4.count { it.status == "SCHEDULED" })

        // 5 teams: odd count adds a bye slot per round, 10 actual matches
        val rr5 = generate(5, TournamentEngine.ROUND_ROBIN)
        assertEquals(10, rr5.size)

        // 6 teams: 15 matches
        val rr6 = generate(6, TournamentEngine.ROUND_ROBIN)
        assertEquals(15, rr6.size)
    }

    @Test
    fun singleEliminationTreeAndByesFor4_6_8Teams() {
        // 4 teams: 2 semi-finals + 1 final = 3 matches
        val se4 = generate(4, TournamentEngine.SINGLE_ELIMINATION)
        assertEquals(3, se4.size)
        assertEquals(0, se4.count { it.status == "BYE" })
        assertEquals("Final", se4.last().round)

        // 6 teams: next power of 2 is 8, size = 7 fixtures with 2 BYE matches
        val se6 = generate(6, TournamentEngine.SINGLE_ELIMINATION)
        assertEquals(7, se6.size)
        assertTrue(se6.any { it.status == "BYE" })

        // 8 teams: 4 quarter-finals + 2 semi-finals + 1 final = 7 matches
        val se8 = generate(8, TournamentEngine.SINGLE_ELIMINATION)
        assertEquals(7, se8.size)
        assertEquals(0, se8.count { it.status == "BYE" })
        assertEquals("Final", se8.last().round)
    }

    @Test
    fun doubleEliminationBracketsAndGrandFinalFor4_6_8Teams() {
        listOf(4, 6, 8).forEach { count ->
            val games = generate(count, TournamentEngine.DOUBLE_ELIMINATION)
            assertTrue(games.any { it.bracketSide == "WINNERS" })
            assertTrue(games.any { it.bracketSide == "LOSERS" })
            assertEquals(1, games.count { it.bracketSide == "GRAND_FINAL" })
            assertEquals(games.size, games.map { it.id }.distinct().size)

            // Verify source IDs are properly populated for downstream matches
            val grandFinal = games.single { it.bracketSide == "GRAND_FINAL" }
            assertNotNull(grandFinal.homeSourceId)
            assertNotNull(grandFinal.awaySourceId)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsFewerThanTwoTeams() {
        generate(1, TournamentEngine.ROUND_ROBIN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDoubleEliminationWithFewerThanFourTeams() {
        generate(3, TournamentEngine.DOUBLE_ELIMINATION)
    }
}
