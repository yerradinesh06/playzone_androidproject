package com.ps22.tournament.data

/** Canonical header order for the Google Sheets / published CSV contract. */
object SheetContract {
    val teams = listOf("teamId", "tournamentId", "name", "shortName", "color", "seed")
    val players = listOf("playerId", "teamId", "name", "number", "position")
    val fixtures = listOf("fixtureId", "tournamentId", "round", "homeTeamId", "awayTeamId", "kickoffAt", "venue", "status", "homeScore", "awayScore", "version", "homeSourceId", "awaySourceId")
    val results = listOf("resultId", "fixtureId", "homeScore", "awayScore", "state", "version", "submittedBy", "submittedAt")
    val standings = listOf("rank", "teamId", "played", "wins", "draws", "losses", "goalsFor", "goalsAgainst", "goalDifference", "points", "updatedAt")
    val playerStats = listOf("playerId", "teamId", "appearances", "goals", "assists", "yellowCards", "redCards", "updatedAt")
}

/** Small RFC4180-style CSV reader supporting quoted commas, escaped quotes and newlines. */
object CsvRows {
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { row += cell.toString(); cell.clear() }
                (c == '\n' || c == '\r') && !quoted -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row += cell.toString(); cell.clear()
                    if (row.any(String::isNotEmpty)) rows += row.toList()
                    row.clear()
                }
                else -> cell.append(c)
            }
            i++
        }
        require(!quoted) { "CSV ended inside a quoted field." }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        return rows
    }
}
