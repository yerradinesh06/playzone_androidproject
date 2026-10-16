package com.ps22.tournament.data

import android.content.Context
import com.ps22.tournament.BuildConfig
import com.ps22.tournament.domain.TournamentEngine
import com.ps22.tournament.notifications.ReminderScheduler
import java.util.UUID
import kotlinx.coroutines.flow.Flow

enum class ActorRole { ORGANISER, TEAM_MANAGER, SPECTATOR }

data class StandingRow(
    val rank: Int,
    val team: TeamEntity,
    val played: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val points: Int,
) {
    val goalDifference: Int get() = goalsFor - goalsAgainst
}

data class ServiceReadiness(
    val sheetsReady: Boolean,
    val spectatorReady: Boolean,
    val appsScriptReady: Boolean,
    val firebaseReady: Boolean,
)

class AccessDenied(message: String) : IllegalStateException(message)
class SetupRequired(message: String) : IllegalStateException(message)

class TournamentRepository(
    private val database: Ps22Database,
    context: Context,
) {
    private val appContext = context.applicationContext
    private val dao = database.tournamentDao()

    fun observeTournaments(): Flow<List<TournamentEntity>> = dao.observeTournaments()
    fun observeCurrentTournament(): Flow<TournamentEntity?> = dao.observeCurrentTournament()
    fun observeTeams(tournamentId: String): Flow<List<TeamEntity>> = dao.observeTeams(tournamentId)
    fun observePlayers(teamId: String): Flow<List<PlayerEntity>> = dao.observePlayers(teamId)
    fun observePlayerStats(teamId: String): Flow<List<PlayerStatsEntity>> = dao.observePlayerStats(teamId)
    fun observeTopScorers(tournamentId: String): Flow<List<PlayerStatsEntity>> = dao.observeTopScorers(tournamentId)
    fun observeFixtures(tournamentId: String): Flow<List<FixtureEntity>> = dao.observeFixtures(tournamentId)
    fun observeRequests(tournamentId: String): Flow<List<SubstitutionRequestEntity>> =
        dao.observeRequests(tournamentId)

    fun readiness() = ServiceReadiness(
        sheetsReady = true,
        spectatorReady = true,
        appsScriptReady = true,
        firebaseReady = true,
    )

    suspend fun initializeDemoIfNeeded() {
        if (dao.tournamentCount() > 0) return
        val tournamentId = "PS22-INVITATIONAL"
        val tournament = TournamentEntity(
            id = tournamentId,
            name = "PS 22 Invitational",
            sport = "Football",
            format = TournamentEngine.ROUND_ROBIN,
            venue = "North Stand Arena",
            announcement = "Welcome to PS 22 Invitational. Round 1 results are published. Round 2 matches are live now.",
        )
        dao.putTournament(tournament)
        val teams = listOf(
            TeamEntity("TEAM-HBR", tournamentId, "Harbor City", "HBR", "#72CFC4", 1),
            TeamEntity("TEAM-NTH", tournamentId, "Northline Athletic", "NTH", "#91B6D9", 2),
            TeamEntity("TEAM-EST", tournamentId, "Eastfield United", "EST", "#F2C66D", 3),
            TeamEntity("TEAM-RVR", tournamentId, "River Rovers", "RVR", "#F07B7D", 4),
        )
        teams.forEach { dao.putTeam(it) }
        val players = listOf(
            PlayerEntity("PL-HBR-01", "TEAM-HBR", "Ari Bennett", 1, "Goalkeeper"),
            PlayerEntity("PL-HBR-08", "TEAM-HBR", "Noah Vale", 8, "Midfielder"),
            PlayerEntity("PL-NTH-01", "TEAM-NTH", "Ravi Shah", 1, "Goalkeeper"),
            PlayerEntity("PL-NTH-09", "TEAM-NTH", "Theo James", 9, "Forward"),
            PlayerEntity("PL-EST-01", "TEAM-EST", "Mika Chen", 1, "Goalkeeper"),
            PlayerEntity("PL-EST-10", "TEAM-EST", "Sam Okafor", 10, "Forward"),
            PlayerEntity("PL-RVR-01", "TEAM-RVR", "Leo Martin", 1, "Goalkeeper"),
            PlayerEntity("PL-RVR-07", "TEAM-RVR", "Ishan Das", 7, "Winger"),
        )
        players.forEach { dao.putPlayer(it) }
        val playerStats = listOf(
            PlayerStatsEntity("PL-HBR-01", "TEAM-HBR", tournamentId, appearances = 3, goals = 0),
            PlayerStatsEntity("PL-HBR-08", "TEAM-HBR", tournamentId, appearances = 3, goals = 2, assists = 1),
            PlayerStatsEntity("PL-NTH-01", "TEAM-NTH", tournamentId, appearances = 3, goals = 0),
            PlayerStatsEntity("PL-NTH-09", "TEAM-NTH", tournamentId, appearances = 3, goals = 3, assists = 0),
            PlayerStatsEntity("PL-EST-01", "TEAM-EST", tournamentId, appearances = 3, goals = 0),
            PlayerStatsEntity("PL-EST-10", "TEAM-EST", tournamentId, appearances = 3, goals = 1, assists = 2),
            PlayerStatsEntity("PL-RVR-01", "TEAM-RVR", tournamentId, appearances = 3, goals = 0),
            PlayerStatsEntity("PL-RVR-07", "TEAM-RVR", tournamentId, appearances = 3, goals = 2, assists = 1),
        )
        playerStats.forEach { dao.putPlayerStats(it) }

        val rawSchedule = TournamentEngine.generate(
            tournamentId = tournamentId,
            teams = teams,
            format = tournament.format,
            startAt = nextMatchDay(),
            venue = tournament.venue,
        )

        // Seed realistic matchday progress so filters (Completed, Live, Upcoming) display distinct matches
        val realisticSchedule = rawSchedule.mapIndexed { index, fixture ->
            when (index) {
                0 -> fixture.copy(status = "PUBLISHED", homeScore = 2, awayScore = 1)
                1 -> fixture.copy(status = "PUBLISHED", homeScore = 3, awayScore = 0)
                2 -> fixture.copy(status = "LIVE", homeScore = 1, awayScore = 1)
                else -> fixture.copy(status = "SCHEDULED")
            }
        }

        dao.putFixtures(realisticSchedule)
        ReminderScheduler.schedule(appContext, realisticSchedule)
    }

    suspend fun createTournament(
        role: ActorRole,
        name: String,
        sport: String,
        format: String,
        venue: String,
    ): TournamentEntity {
        requireOrganiser(role)
        val cleanName = name.trim()
        require(cleanName.length in 3..48) { "Tournament name must be 3–48 characters." }
        require(sport.isNotBlank()) { "Choose a sport." }
        require(venue.trim().length <= 64) { "Venue must be 64 characters or fewer." }
        require(format in setOf(
            TournamentEngine.ROUND_ROBIN,
            TournamentEngine.SINGLE_ELIMINATION,
            TournamentEngine.DOUBLE_ELIMINATION,
        )) { "Choose a supported tournament format." }
        return TournamentEntity(
            id = "T-${UUID.randomUUID().toString().take(8).uppercase()}",
            name = cleanName,
            sport = sport.trim(),
            format = format,
            venue = venue.trim().ifBlank { "Main Arena" },
        ).also { dao.putTournament(it) }
    }

    suspend fun addTeam(role: ActorRole, tournamentId: String, name: String): TeamEntity {
        requireOrganiser(role)
        val clean = name.trim()
        require(clean.length in 2..32) { "Team name must be 2–32 characters." }
        val teams = dao.teams(tournamentId)
        require(teams.none { it.name.equals(clean, ignoreCase = true) }) { "That team is already registered in this tournament." }
        val palette = listOf("#72CFC4", "#91B6D9", "#F0B965", "#F07B7D", "#82C7A6", "#B29BFF")
        val initials = clean.split(Regex("\\s+")).take(3).joinToString("") { it.take(1).uppercase() }.take(3)
        return TeamEntity(
            id = "TEAM-${UUID.randomUUID().toString().take(8).uppercase()}",
            tournamentId = tournamentId,
            name = clean,
            shortName = initials,
            color = palette[teams.size % palette.size],
            seed = teams.size + 1,
        ).also { dao.putTeam(it) }
    }

    suspend fun addPlayer(role: ActorRole, teamId: String, name: String, number: Int, position: String): PlayerEntity {
        requireOrganiser(role)
        val clean = name.trim()
        require(clean.length in 2..40) { "Player name must be 2–40 characters." }
        require(number in 1..99) { "Jersey number must be between 1 and 99." }
        require(position.isNotBlank() && position.length <= 24) { "Add a position of 24 characters or fewer." }
        return PlayerEntity("PLAYER-${UUID.randomUUID().toString().take(8).uppercase()}", teamId, clean, number, position.trim())
            .also { dao.putPlayer(it) }
    }

    suspend fun generateFixtures(role: ActorRole, tournamentId: String): Int {
        requireOrganiser(role)
        val tournament = dao.currentTournament()?.takeIf { it.id == tournamentId }
            ?: throw IllegalStateException("Tournament is no longer available.")
        val teams = dao.teams(tournamentId)
        val fixtures = TournamentEngine.generate(
            tournamentId,
            teams,
            tournament.format,
            nextMatchDay(),
            tournament.venue,
        )
        dao.replaceFixtures(tournamentId, fixtures)
        fixtures.filter { it.status == "BYE" }.forEach { bye -> advanceBracket(tournamentId, bye) }
        ReminderScheduler.schedule(appContext, fixtures)
        return fixtures.count { it.status != "BYE" && it.status != "VOID" }
    }

    suspend fun setAnnouncement(role: ActorRole, tournamentId: String, text: String) {
        requireOrganiser(role)
        val tournament = dao.currentTournament()?.takeIf { it.id == tournamentId }
            ?: throw IllegalStateException("Tournament is no longer available.")
        require(text.length <= 220) { "Announcement must be 220 characters or fewer." }
        dao.putTournament(tournament.copy(announcement = text.trim()))
    }

    suspend fun markLive(role: ActorRole, fixtureId: String): FixtureEntity {
        requireOrganiser(role)
        val fixture = dao.fixture(fixtureId) ?: error("Fixture not found.")
        require(fixture.status == "SCHEDULED") { "Only a scheduled fixture can go live." }
        if (dao.transitionLive(fixtureId, fixture.version) != 1) {
            throw IllegalStateException("This match status changed. Refresh before updating it.")
        }
        return fixture.copy(status = "LIVE", version = fixture.version + 1)
    }

    suspend fun submitResult(
        role: ActorRole,
        fixtureId: String,
        homeScore: Int,
        awayScore: Int,
    ): FixtureEntity {
        requireOrganiser(role)
        require(homeScore in 0..99 && awayScore in 0..99) { "Scores must be between 0 and 99." }
        val fixture = dao.fixture(fixtureId) ?: error("Fixture not found.")
        require(fixture.status == "SCHEDULED" || fixture.status == "LIVE") {
            "This fixture already has a published result."
        }
        require(fixture.homeTeamId != null && fixture.awayTeamId != null) {
            "Both teams must advance before a result can be entered."
        }
        if (fixture.bracketSide.isNotBlank() && homeScore == awayScore) {
            throw IllegalArgumentException("Elimination matches need a winner before the result can be saved.")
        }
        val persistedState = "PUBLISHED"
        val saved = fixture.copy(
            homeScore = homeScore,
            awayScore = awayScore,
            status = persistedState,
            version = fixture.version + 1,
        )
        if (dao.commitResult(fixtureId, fixture.version, persistedState, homeScore, awayScore) != 1) {
            throw IllegalStateException("This match already has a result. Refresh before updating it.")
        }
        advanceBracket(fixture.tournamentId, saved)
        if (saved.bracketSide == "GRAND_FINAL" && awayScore > homeScore) {
            val reset = FixtureEntity(
                id = "${saved.id}-RESET",
                tournamentId = saved.tournamentId,
                round = "Reset final",
                homeTeamId = saved.awayTeamId,
                awayTeamId = saved.homeTeamId,
                kickoffAt = saved.kickoffAt + 60 * 60 * 1000L,
                venue = saved.venue,
                status = "SCHEDULED",
                bracketSide = "RESET",
                gameNumber = saved.gameNumber + 1,
            )
            dao.putFixture(reset)
        }
        return saved
    }

    suspend fun submitSubstitutionRequest(
        role: ActorRole,
        tournamentId: String,
        teamId: String,
        assignedTeamId: String,
        playerOut: String,
        playerIn: String,
        reason: String,
    ) {
        if (role != ActorRole.TEAM_MANAGER) throw AccessDenied("Only an assigned team manager can request a substitution.")
        require(dao.team(teamId)?.tournamentId == tournamentId) { "Choose a team in this tournament." }
        require(assignedTeamId == teamId) { "Managers can request substitutions for their assigned team only." }
        require(playerOut.isNotBlank() && playerIn.isNotBlank() && playerOut != playerIn) {
            "Choose two different players."
        }
        val roster = dao.players(teamId).map { it.name }.toSet()
        require(playerOut.trim() in roster && playerIn.trim() in roster) { "Choose both players from your assigned team's roster." }
        require(reason.length <= 160) { "Keep the reason to 160 characters or fewer." }
        dao.putRequest(
            SubstitutionRequestEntity(
                id = "SUB-${UUID.randomUUID().toString().take(8).uppercase()}",
                tournamentId = tournamentId,
                teamId = teamId,
                playerOut = playerOut.trim(),
                playerIn = playerIn.trim(),
                reason = reason.trim(),
            ),
        )
    }

    suspend fun resolveSubstitution(role: ActorRole, request: SubstitutionRequestEntity, approve: Boolean) {
        requireOrganiser(role)
        dao.putRequest(request.copy(state = if (approve) "APPROVED" else "DECLINED"))
    }

    suspend fun standings(tournamentId: String): List<StandingRow> {
        val teams = dao.teams(tournamentId)
        val played = dao.fixtures(tournamentId).filter { it.status == "PUBLISHED" }
        val rows = teams.map { team ->
            var wins = 0
            var draws = 0
            var losses = 0
            var goalsFor = 0
            var goalsAgainst = 0
            played.filter { it.homeTeamId == team.id || it.awayTeamId == team.id }.forEach { fixture ->
                val home = fixture.homeScore ?: return@forEach
                val away = fixture.awayScore ?: return@forEach
                val isHome = fixture.homeTeamId == team.id
                val own = if (isHome) home else away
                val other = if (isHome) away else home
                goalsFor += own
                goalsAgainst += other
                when {
                    own > other -> wins++
                    own == other -> draws++
                    else -> losses++
                }
            }
            StandingRow(
                rank = 0,
                team = team,
                played = wins + draws + losses,
                wins = wins,
                draws = draws,
                losses = losses,
                goalsFor = goalsFor,
                goalsAgainst = goalsAgainst,
                points = wins * 3 + draws,
            )
        }.sortedWith(
            compareByDescending<StandingRow> { it.points }
                .thenByDescending { it.goalDifference }
                .thenByDescending { it.goalsFor }
                .thenBy { it.team.name },
        )
        return rows.mapIndexed { index, row -> row.copy(rank = index + 1) }
    }

    private suspend fun advanceBracket(tournamentId: String, first: FixtureEntity) {
        val all = dao.fixtures(tournamentId).associateBy { it.id }.toMutableMap()
        val pending = ArrayDeque<FixtureEntity>()
        pending.add(first)
        while (pending.isNotEmpty()) {
            val completed = pending.removeFirst()
            val winner = when {
                completed.status == "BYE" -> completed.homeTeamId ?: completed.awayTeamId
                completed.status == "PUBLISHED" && (completed.homeScore ?: -1) > (completed.awayScore ?: -1) -> completed.homeTeamId
                completed.status == "PUBLISHED" && (completed.awayScore ?: -1) > (completed.homeScore ?: -1) -> completed.awayTeamId
                else -> null
            }
            val loser = when {
                completed.status == "PUBLISHED" && (completed.homeScore ?: -1) > (completed.awayScore ?: -1) -> completed.awayTeamId
                completed.status == "PUBLISHED" && (completed.awayScore ?: -1) > (completed.homeScore ?: -1) -> completed.homeTeamId
                else -> null
            }
            all.values.filter { it.status == "PENDING" }.toList().forEach { target ->
                var changed = false
                var updated = target
                if (target.homeSourceId == completed.id) {
                    updated = updated.copy(
                        homeTeamId = if (target.homeSourceOutcome == "LOSER") loser else winner,
                        homeSourceResolved = true,
                    )
                    changed = true
                }
                if (target.awaySourceId == completed.id) {
                    updated = updated.copy(
                        awayTeamId = if (target.awaySourceOutcome == "LOSER") loser else winner,
                        awaySourceResolved = true,
                    )
                    changed = true
                }
                if (!changed) return@forEach
                if (updated.homeSourceResolved && updated.awaySourceResolved) {
                    val teamsPresent = listOfNotNull(updated.homeTeamId, updated.awayTeamId).size
                    updated = updated.copy(
                        status = when (teamsPresent) {
                            2 -> "SCHEDULED"
                            1 -> "BYE"
                            else -> "VOID"
                        },
                    )
                }
                all[updated.id] = updated
                if (updated.status == "BYE" || updated.status == "VOID") pending.add(updated)
            }
        }
        dao.putFixtures(all.values.toList())
    }

    private fun requireOrganiser(role: ActorRole) {
        if (role != ActorRole.ORGANISER) throw AccessDenied("This action is available to tournament organisers only.")
    }

    private fun nextMatchDay(): Long {
        val now = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            add(java.util.Calendar.DAY_OF_YEAR, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 17)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
}
