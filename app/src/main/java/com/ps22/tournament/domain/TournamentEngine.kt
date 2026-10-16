package com.ps22.tournament.domain

import com.ps22.tournament.data.FixtureEntity
import com.ps22.tournament.data.TeamEntity
import kotlin.math.ceil
import kotlin.math.log2

object TournamentEngine {
    const val ROUND_ROBIN = "Round robin"
    const val SINGLE_ELIMINATION = "Single elimination"
    const val DOUBLE_ELIMINATION = "Double elimination"

    fun generate(
        tournamentId: String,
        teams: List<TeamEntity>,
        format: String,
        startAt: Long,
        venue: String,
    ): List<FixtureEntity> {
        require(teams.size >= 2) { "Add at least two teams before generating fixtures." }
        require(format != DOUBLE_ELIMINATION || teams.size >= 4) {
            "Double elimination needs at least four teams."
        }
        return when (format) {
            ROUND_ROBIN -> roundRobin(tournamentId, teams, startAt, venue)
            SINGLE_ELIMINATION -> singleElimination(tournamentId, teams, startAt, venue)
            DOUBLE_ELIMINATION -> doubleElimination(tournamentId, teams, startAt, venue)
            else -> error("Choose a supported tournament format.")
        }
    }

    private fun roundRobin(
        tournamentId: String,
        teams: List<TeamEntity>,
        startAt: Long,
        venue: String,
    ): List<FixtureEntity> {
        val rotation: MutableList<String?> = teams.sortedBy { it.seed }.map { it.id as String? }.toMutableList()
        if (rotation.size % 2 == 1) rotation += null
        val totalRounds = rotation.size - 1
        val half = rotation.size / 2
        val fixtures = mutableListOf<FixtureEntity>()
        var game = 1

        repeat(totalRounds) { roundIndex ->
            repeat(half) { pairIndex ->
                val first = rotation[pairIndex]
                val second = rotation[rotation.lastIndex - pairIndex]
                if (first != null && second != null) {
                    val switchHome = (roundIndex + pairIndex) % 2 == 1
                    fixtures += FixtureEntity(
                        id = fixtureId(tournamentId, "RR", roundIndex + 1, pairIndex + 1),
                        tournamentId = tournamentId,
                        round = "Round ${roundIndex + 1}",
                        homeTeamId = if (switchHome) second else first,
                        awayTeamId = if (switchHome) first else second,
                        kickoffAt = startAt + roundIndex * DAY + pairIndex * 90 * 60 * 1000L,
                        venue = venue,
                        gameNumber = game++,
                    )
                }
            }
            val fixed = rotation.first()
            val tail = rotation.drop(1).toMutableList()
            tail.add(0, tail.removeAt(tail.lastIndex))
            rotation.clear()
            rotation += fixed
            rotation += tail
        }
        return fixtures
    }

    private fun singleElimination(
        tournamentId: String,
        teams: List<TeamEntity>,
        startAt: Long,
        venue: String,
    ): List<FixtureEntity> {
        val size = nextPowerOfTwo(teams.size)
        val rounds = ceil(log2(size.toDouble())).toInt()
        val seeded = seedOrder(size).map { seed -> teams.sortedBy { it.seed }.getOrNull(seed - 1)?.id }
        val fixtures = mutableListOf<FixtureEntity>()
        var previousRound = emptyList<FixtureEntity>()
        var gameNumber = 1

        repeat(rounds) { roundIndex ->
            val matchCount = size shr (roundIndex + 1)
            val currentRound = (0 until matchCount).map { matchIndex ->
                val id = fixtureId(tournamentId, "W", roundIndex + 1, matchIndex + 1)
                val isOpeningRound = roundIndex == 0
                val home = if (isOpeningRound) seeded[matchIndex * 2] else null
                val away = if (isOpeningRound) seeded[matchIndex * 2 + 1] else null
                val hasBye = isOpeningRound && (home == null || away == null)
                FixtureEntity(
                    id = id,
                    tournamentId = tournamentId,
                    round = roundLabel(teams.size, roundIndex),
                    homeTeamId = home,
                    awayTeamId = away,
                    kickoffAt = startAt + roundIndex * DAY,
                    venue = venue,
                    status = if (hasBye) "BYE" else if (isOpeningRound) "SCHEDULED" else "PENDING",
                    homeScore = null,
                    awayScore = null,
                    bracketSide = "WINNERS",
                    gameNumber = gameNumber++,
                    homeSourceId = previousRound.getOrNull(matchIndex * 2)?.id,
                    awaySourceId = previousRound.getOrNull(matchIndex * 2 + 1)?.id,
                    homeSourceResolved = isOpeningRound,
                    awaySourceResolved = isOpeningRound,
                ).also(fixtures::add)
            }
            previousRound = currentRound
        }
        return fixtures
    }

    private fun doubleElimination(
        tournamentId: String,
        teams: List<TeamEntity>,
        startAt: Long,
        venue: String,
    ): List<FixtureEntity> {
        val size = nextPowerOfTwo(teams.size)
        val winnerRoundCount = ceil(log2(size.toDouble())).toInt()
        val orderedTeams = teams.sortedBy { it.seed }
        val seeds = seedOrder(size).map { seed -> orderedTeams.getOrNull(seed - 1)?.id }
        val winnerRounds = mutableListOf<List<FixtureEntity>>()
        val all = mutableListOf<FixtureEntity>()
        var game = 1

        repeat(winnerRoundCount) { roundIndex ->
            val count = size shr (roundIndex + 1)
            val first = roundIndex == 0
            val current = (0 until count).map { index ->
                val home = if (first) seeds[index * 2] else null
                val away = if (first) seeds[index * 2 + 1] else null
                FixtureEntity(
                    id = fixtureId(tournamentId, "W", roundIndex + 1, index + 1),
                    tournamentId = tournamentId,
                    round = "Winners · ${roundLabel(teams.size, roundIndex)}",
                    homeTeamId = home,
                    awayTeamId = away,
                    kickoffAt = startAt + roundIndex * DAY,
                    venue = venue,
                    status = if (first && (home == null || away == null)) "BYE" else if (first) "SCHEDULED" else "PENDING",
                    bracketSide = "WINNERS",
                    gameNumber = game++,
                    homeSourceId = winnerRounds.getOrNull(roundIndex - 1)?.getOrNull(index * 2)?.id,
                    awaySourceId = winnerRounds.getOrNull(roundIndex - 1)?.getOrNull(index * 2 + 1)?.id,
                    homeSourceResolved = first,
                    awaySourceResolved = first,
                )
            }
            winnerRounds += current
            all += current
        }

        val firstLoserRound = mutableListOf<FixtureEntity>()
        val firstLoserCount = size / 4
        repeat(firstLoserCount) { index ->
            firstLoserRound += FixtureEntity(
                id = fixtureId(tournamentId, "L", 1, index + 1),
                tournamentId = tournamentId,
                round = "Losers · Round 1",
                homeTeamId = null,
                awayTeamId = null,
                kickoffAt = startAt + DAY,
                venue = venue,
                status = "PENDING",
                bracketSide = "LOSERS",
                gameNumber = game++,
                homeSourceId = winnerRounds.first().getOrNull(index * 2)?.id,
                awaySourceId = winnerRounds.first().getOrNull(index * 2 + 1)?.id,
                homeSourceOutcome = "LOSER",
                awaySourceOutcome = "LOSER",
                homeSourceResolved = false,
                awaySourceResolved = false,
            )
        }
        all += firstLoserRound
        var survivors: List<FixtureEntity> = firstLoserRound
        var loserRound = 1

        for (winnerRoundIndex in 1 until winnerRoundCount) {
            val incoming = winnerRounds[winnerRoundIndex]
            if (survivors.size > incoming.size) {
                val consolidated = (0 until incoming.size).map { index ->
                    FixtureEntity(
                        id = fixtureId(tournamentId, "L", ++loserRound, index + 1),
                        tournamentId = tournamentId,
                        round = "Losers · Round $loserRound",
                        homeTeamId = null,
                        awayTeamId = null,
                        kickoffAt = startAt + loserRound * DAY,
                        venue = venue,
                        status = "PENDING",
                        bracketSide = "LOSERS",
                        gameNumber = game++,
                        homeSourceId = survivors.getOrNull(index * 2)?.id,
                        awaySourceId = survivors.getOrNull(index * 2 + 1)?.id,
                        homeSourceResolved = false,
                        awaySourceResolved = false,
                    )
                }
                all += consolidated
                survivors = consolidated
            }
            val withLosers = incoming.indices.map { index ->
                FixtureEntity(
                    id = fixtureId(tournamentId, "L", ++loserRound, index + 1),
                    tournamentId = tournamentId,
                    round = "Losers · Round $loserRound",
                    homeTeamId = null,
                    awayTeamId = null,
                    kickoffAt = startAt + loserRound * DAY,
                    venue = venue,
                    status = "PENDING",
                    bracketSide = "LOSERS",
                    gameNumber = game++,
                    homeSourceId = survivors.getOrNull(index)?.id,
                    awaySourceId = incoming[index].id,
                    awaySourceOutcome = "LOSER",
                    homeSourceResolved = false,
                    awaySourceResolved = false,
                )
            }
            all += withLosers
            survivors = withLosers
        }

        val winnerChampion = winnerRounds.last().single()
        val loserChampion = survivors.single()
        all += FixtureEntity(
            id = fixtureId(tournamentId, "GF", 1, 1),
            tournamentId = tournamentId,
            round = "Grand final",
            homeTeamId = null,
            awayTeamId = null,
            kickoffAt = startAt + (winnerRoundCount + loserRound + 1) * DAY,
            venue = venue,
            status = "PENDING",
            bracketSide = "GRAND_FINAL",
            gameNumber = game,
            homeSourceId = winnerChampion.id,
            awaySourceId = loserChampion.id,
            homeSourceResolved = false,
            awaySourceResolved = false,
        )
        return all
    }

    private fun seedOrder(size: Int): List<Int> {
        var seeds = listOf(1, 2)
        var bracketSize = 2
        while (bracketSize < size) {
            val nextSize = bracketSize * 2
            seeds = seeds.flatMap { seed -> listOf(seed, nextSize + 1 - seed) }
            bracketSize = nextSize
        }
        return seeds
    }

    private fun nextPowerOfTwo(value: Int): Int {
        var result = 1
        while (result < value) result *= 2
        return result
    }

    private fun roundLabel(teamCount: Int, roundIndex: Int): String {
        val participants = (nextPowerOfTwo(teamCount) shr roundIndex)
        return when (participants) {
            2 -> "Final"
            4 -> "Semi-finals"
            8 -> "Quarter-finals"
            else -> "Round of $participants"
        }
    }

    private fun fixtureId(tournamentId: String, side: String, round: Int, match: Int) =
        "FIX-$tournamentId-$side${round.toString().padStart(2, '0')}-${match.toString().padStart(2, '0')}"

    private const val DAY = 24 * 60 * 60 * 1000L
}
