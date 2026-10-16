package com.ps22.tournament.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TournamentRepositoryTest {

    private fun team(id: String, name: String, seed: Int) =
        TeamEntity(id = id, tournamentId = "TOUR-1", name = name, shortName = name.take(3), color = "#005F5B", seed = seed)

    @Test
    fun standingsSortedByPointsGoalDiffGoalsForAndName() {
        val teamA = team("T1", "Alpha", 1)
        val teamB = team("T2", "Beta", 2)
        val teamC = team("T3", "Gamma", 3)

        // Simulate calculated standing rows
        val rows = listOf(
            StandingRow(rank = 0, team = teamB, played = 2, wins = 1, draws = 1, losses = 0, goalsFor = 4, goalsAgainst = 2, points = 4),
            StandingRow(rank = 0, team = teamA, played = 2, wins = 2, draws = 0, losses = 0, goalsFor = 5, goalsAgainst = 1, points = 6),
            StandingRow(rank = 0, team = teamC, played = 2, wins = 0, draws = 1, losses = 1, goalsFor = 2, goalsAgainst = 4, points = 1),
        )

        val sorted = rows.sortedWith(
            compareByDescending<StandingRow> { it.points }
                .thenByDescending { it.goalDifference }
                .thenByDescending { it.goalsFor }
                .thenBy { it.team.name }
        ).mapIndexed { idx, row -> row.copy(rank = idx + 1) }

        assertEquals("Alpha", sorted[0].team.name)
        assertEquals(1, sorted[0].rank)
        assertEquals("Beta", sorted[1].team.name)
        assertEquals(2, sorted[1].rank)
        assertEquals("Gamma", sorted[2].team.name)
        assertEquals(3, sorted[2].rank)
    }

    @Test
    fun standingRowGoalDifferenceCalculation() {
        val row = StandingRow(
            rank = 1,
            team = team("T1", "Delta", 1),
            played = 3,
            wins = 2,
            draws = 1,
            losses = 0,
            goalsFor = 7,
            goalsAgainst = 3,
            points = 7,
        )
        assertEquals(4, row.goalDifference)
    }

    @Test
    fun sheetContractContainsExpectedColumns() {
        assertEquals(6, SheetContract.teams.size)
        assertEquals(5, SheetContract.players.size)
        assertEquals(13, SheetContract.fixtures.size)
        assertEquals(8, SheetContract.results.size)
        assertEquals(11, SheetContract.standings.size)
        assertEquals(8, SheetContract.playerStats.size)
    }
}
