package com.ps22.tournament.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `player_stats` (`playerId` TEXT NOT NULL, `teamId` TEXT NOT NULL, `tournamentId` TEXT NOT NULL, `appearances` INTEGER NOT NULL DEFAULT 0, `goals` INTEGER NOT NULL DEFAULT 0, `assists` INTEGER NOT NULL DEFAULT 0, `yellowCards` INTEGER NOT NULL DEFAULT 0, `redCards` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`playerId`))")
    }
}

@Dao
interface VenueBookingDao {
    // User Queries
    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUser(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    fun observeUser(id: String): Flow<UserEntity?>

    @Query("SELECT * FROM users ORDER BY createdAt DESC")
    fun observeAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users ORDER BY createdAt DESC")
    suspend fun getAllUsers(): List<UserEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putUser(user: UserEntity)

    // Venue Queries
    @Query("SELECT * FROM venues ORDER BY rating DESC, createdAt DESC")
    fun observeAllVenues(): Flow<List<VenueEntity>>

    @Query("SELECT * FROM venues WHERE managerId = :managerId OR managerId = 'mgr_ps22_demo' OR (:managerEmail != '' AND LOWER(email) = LOWER(:managerEmail)) ORDER BY createdAt DESC")
    fun observeManagerVenues(managerId: String, managerEmail: String): Flow<List<VenueEntity>>

    @Query("SELECT * FROM venues WHERE id = :id LIMIT 1")
    fun observeVenue(id: String): Flow<VenueEntity?>

    @Query("SELECT * FROM venues WHERE id = :id LIMIT 1")
    suspend fun getVenue(id: String): VenueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putVenue(venue: VenueEntity)

    @Query("DELETE FROM venues WHERE id = :venueId")
    suspend fun deleteVenue(venueId: String)

    // Equipment Queries
    @Query("SELECT * FROM equipment WHERE venueId = :venueId ORDER BY sport, name")
    fun observeEquipment(venueId: String): Flow<List<EquipmentEntity>>

    @Query("SELECT * FROM equipment WHERE venueId = :venueId AND sport = :sport")
    suspend fun getEquipmentForSport(venueId: String, sport: String): List<EquipmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putEquipment(equipment: EquipmentEntity)

    @Query("DELETE FROM equipment WHERE id = :equipmentId")
    suspend fun deleteEquipment(equipmentId: String)

    // Session / Availability Queries
    @Query("SELECT * FROM sessions WHERE venueId = :venueId AND date = :date ORDER BY startTime")
    fun observeSessions(venueId: String, date: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE venueId = :venueId ORDER BY date, startTime")
    fun observeAllSessionsForVenue(venueId: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE venueId IN (SELECT id FROM venues WHERE managerId = :managerId OR managerId = 'mgr_ps22_demo' OR (:managerEmail != '' AND LOWER(email) = LOWER(:managerEmail))) ORDER BY date, startTime")
    fun observeManagerSessions(managerId: String, managerEmail: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions")
    suspend fun getAllSessions(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE venueId = :venueId AND date = :date AND sport = :sport ORDER BY startTime")
    suspend fun getSessionsForDate(venueId: String, date: String, sport: String): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getSession(id: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSession(session: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSessions(sessions: List<SessionEntity>)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    // Booking Queries
    @Query("SELECT * FROM bookings WHERE playerId = :playerId OR LOWER(playerEmail) = LOWER(:playerEmail) ORDER BY createdAt DESC")
    fun observePlayerBookings(playerId: String, playerEmail: String): Flow<List<BookingEntity>>

    @Query("SELECT * FROM bookings WHERE venueId = :venueId ORDER BY createdAt DESC")
    fun observeVenueBookings(venueId: String): Flow<List<BookingEntity>>

    @Query("SELECT * FROM bookings WHERE venueId IN (SELECT id FROM venues WHERE managerId = :managerId OR managerId = 'mgr_ps22_demo' OR (:managerEmail != '' AND LOWER(email) = LOWER(:managerEmail))) ORDER BY createdAt DESC")
    fun observeManagerBookings(managerId: String, managerEmail: String): Flow<List<BookingEntity>>

    @Query("SELECT * FROM bookings WHERE id = :id LIMIT 1")
    suspend fun getBooking(id: String): BookingEntity?

    @Query("SELECT * FROM bookings WHERE venueId = :venueId AND date = :date AND startTime = :startTime AND endTime = :endTime AND status = 'CONFIRMED' LIMIT 1")
    suspend fun findConfirmedConflict(venueId: String, date: String, startTime: String, endTime: String): BookingEntity?

    @Query("SELECT * FROM bookings WHERE venueId = :venueId AND date = :date AND status = 'CONFIRMED'")
    suspend fun getConfirmedBookingsForDate(venueId: String, date: String): List<BookingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putBooking(booking: BookingEntity)

    @Query("UPDATE bookings SET status = :status, rejectionReason = :reason, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateBookingStatus(id: String, status: String, reason: String, updatedAt: Long)

    @Query("SELECT * FROM bookings WHERE sessionId = :sessionId")
    suspend fun getBookingsForSession(sessionId: String): List<BookingEntity>

    @Query("SELECT * FROM bookings WHERE id = :id LIMIT 1")
    fun observeBooking(id: String): Flow<BookingEntity?>

    @Query("DELETE FROM bookings WHERE id = :id")
    suspend fun deleteBooking(id: String)

    @Query("SELECT * FROM sessions WHERE venueId = :venueId")
    suspend fun getAllSessionsForVenue(venueId: String): List<SessionEntity>

    @Query("DELETE FROM equipment WHERE venueId = :venueId")
    suspend fun deleteEquipmentForVenue(venueId: String)

    // Notifications Queries
    @Query("SELECT * FROM notifications WHERE userId = :userId OR LOWER(userId) = LOWER(:userEmail) ORDER BY timestamp DESC")
    fun observeNotifications(userId: String, userEmail: String): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putNotification(notification: NotificationEntity)

    @Query("UPDATE notifications SET isRead = 1 WHERE userId = :userId OR LOWER(userId) = LOWER(:userEmail)")
    suspend fun markNotificationsRead(userId: String, userEmail: String)

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteNotification(id: String)

    @Query("DELETE FROM notifications WHERE userId = :userId OR LOWER(userId) = LOWER(:userEmail)")
    suspend fun clearAllNotifications(userId: String, userEmail: String)

    // Review Queries
    @Query("SELECT * FROM reviews WHERE venueId = :venueId ORDER BY createdAt DESC")
    fun observeReviews(venueId: String): Flow<List<ReviewEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putReview(review: ReviewEntity)

    // Scoreboard Queries
    @Query("SELECT * FROM match_scoreboards WHERE bookingId = :bookingId LIMIT 1")
    fun observeScoreboardForBooking(bookingId: String): Flow<MatchScoreboardEntity?>

    @Query("SELECT * FROM match_scoreboards WHERE id = :id LIMIT 1")
    fun observeScoreboard(id: String): Flow<MatchScoreboardEntity?>

    @Query("SELECT * FROM match_scoreboards WHERE id = :id LIMIT 1")
    suspend fun getScoreboard(id: String): MatchScoreboardEntity?

    @Query("SELECT * FROM match_scoreboards WHERE status = 'LIVE' ORDER BY updatedAt DESC")
    fun observeLiveScoreboards(): Flow<List<MatchScoreboardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putScoreboard(scoreboard: MatchScoreboardEntity)

    @Query("UPDATE match_scoreboards SET status = 'CANCELLED' WHERE bookingId = :bookingId")
    suspend fun cancelScoreboardForBooking(bookingId: String)

    @Query("SELECT * FROM scoreboard_events WHERE matchId = :matchId ORDER BY createdAt DESC")
    fun observeScoreboardEvents(matchId: String): Flow<List<ScoreboardEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putScoreboardEvent(event: ScoreboardEventEntity)

    @Query("SELECT COUNT(*) FROM venues")
    suspend fun venueCount(): Int
}

@Dao
interface TournamentDao {
    @Query("SELECT * FROM tournaments ORDER BY createdAt DESC")
    fun observeTournaments(): Flow<List<TournamentEntity>>

    @Query("SELECT * FROM tournaments ORDER BY createdAt DESC LIMIT 1")
    fun observeCurrentTournament(): Flow<TournamentEntity?>

    @Query("SELECT * FROM tournaments ORDER BY createdAt DESC LIMIT 1")
    suspend fun currentTournament(): TournamentEntity?

    @Query("SELECT * FROM teams WHERE tournamentId = :tournamentId ORDER BY seed, name")
    fun observeTeams(tournamentId: String): Flow<List<TeamEntity>>

    @Query("SELECT * FROM teams WHERE tournamentId = :tournamentId ORDER BY seed, name")
    suspend fun teams(tournamentId: String): List<TeamEntity>

    @Query("SELECT * FROM teams WHERE id = :id LIMIT 1")
    suspend fun team(id: String): TeamEntity?

    @Query("SELECT * FROM players WHERE teamId = :teamId ORDER BY number, name")
    fun observePlayers(teamId: String): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE teamId = :teamId ORDER BY number, name")
    suspend fun players(teamId: String): List<PlayerEntity>

    @Query("SELECT * FROM player_stats WHERE teamId = :teamId")
    fun observePlayerStats(teamId: String): Flow<List<PlayerStatsEntity>>

    @Query("SELECT * FROM player_stats WHERE tournamentId = :tournamentId ORDER BY goals DESC, appearances DESC")
    fun observeTopScorers(tournamentId: String): Flow<List<PlayerStatsEntity>>

    @Query("SELECT * FROM player_stats WHERE playerId = :playerId LIMIT 1")
    suspend fun playerStats(playerId: String): PlayerStatsEntity?

    @Query("SELECT * FROM fixtures WHERE tournamentId = :tournamentId ORDER BY kickoffAt, gameNumber")
    fun observeFixtures(tournamentId: String): Flow<List<FixtureEntity>>

    @Query("SELECT * FROM fixtures WHERE id = :id LIMIT 1")
    suspend fun fixture(id: String): FixtureEntity?

    @Query("UPDATE fixtures SET status = :status, homeScore = :homeScore, awayScore = :awayScore, version = version + 1 WHERE id = :id AND version = :expectedVersion AND status IN ('SCHEDULED','LIVE')")
    suspend fun commitResult(id: String, expectedVersion: Long, status: String, homeScore: Int, awayScore: Int): Int

    @Query("UPDATE fixtures SET status = 'LIVE', version = version + 1 WHERE id = :id AND version = :expectedVersion AND status = 'SCHEDULED'")
    suspend fun transitionLive(id: String, expectedVersion: Long): Int

    @Query("SELECT * FROM fixtures WHERE tournamentId = :tournamentId ORDER BY kickoffAt, gameNumber")
    suspend fun fixtures(tournamentId: String): List<FixtureEntity>

    @Query("SELECT * FROM substitution_requests WHERE tournamentId = :tournamentId ORDER BY createdAt DESC")
    fun observeRequests(tournamentId: String): Flow<List<SubstitutionRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTournament(tournament: TournamentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTeam(team: TeamEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPlayer(player: PlayerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPlayerStats(stats: PlayerStatsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putFixture(fixture: FixtureEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putFixtures(fixtures: List<FixtureEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putRequest(request: SubstitutionRequestEntity)

    @Query("DELETE FROM fixtures WHERE tournamentId = :tournamentId")
    suspend fun deleteFixtures(tournamentId: String)

    @Query("SELECT COUNT(*) FROM tournaments")
    suspend fun tournamentCount(): Int

    @Query("SELECT COUNT(*) FROM fixtures WHERE tournamentId = :tournamentId")
    suspend fun fixtureCount(tournamentId: String): Int

    @Transaction
    suspend fun replaceFixtures(tournamentId: String, fixtures: List<FixtureEntity>) {
        deleteFixtures(tournamentId)
        putFixtures(fixtures)
    }
}

@Database(
    entities = [
        UserEntity::class,
        VenueEntity::class,
        EquipmentEntity::class,
        SessionEntity::class,
        BookingEntity::class,
        NotificationEntity::class,
        ReviewEntity::class,
        MatchScoreboardEntity::class,
        ScoreboardEventEntity::class,
        TournamentEntity::class,
        TeamEntity::class,
        PlayerEntity::class,
        PlayerStatsEntity::class,
        FixtureEntity::class,
        SubstitutionRequestEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class Ps22Database : RoomDatabase() {
    abstract fun venueBookingDao(): VenueBookingDao
    abstract fun tournamentDao(): TournamentDao
}
