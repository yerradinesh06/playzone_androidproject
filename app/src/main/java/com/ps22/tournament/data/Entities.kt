package com.ps22.tournament.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val googleAccountId: String,
    val name: String,
    val email: String,
    val phone: String = "",
    val photoUrl: String = "",
    val role: String, // VENUE_MANAGER or PLAYER
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "venues", indices = [Index("managerId")])
data class VenueEntity(
    @PrimaryKey val id: String,
    val managerId: String,
    val name: String,
    val venueType: String, // Arena, Turf, Court, Ground, Complex
    val supportedSports: String, // Comma-separated: Football, Cricket, Badminton, Basketball, Volleyball, Tennis
    val description: String,
    val phone: String,
    val email: String,
    val address: String,
    val area: String,
    val city: String,
    val operatingHours: String,
    val facilities: String, // Parking, Changing Room, Washroom, Drinking Water, Wi-Fi, Night Lighting
    val rules: String,
    val cancellationPolicy: String,
    val coverImage: String,
    val images: String, // Comma-separated URLs/resources
    val rating: Float = 4.8f,
    val reviewCount: Int = 12,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "equipment", indices = [Index("venueId"), Index("sport")])
data class EquipmentEntity(
    @PrimaryKey val id: String,
    val venueId: String,
    val sport: String,
    val name: String,
    val quantity: Int,
    val isIncludedFree: Boolean = true,
    val additionalCharge: Double = 0.0,
    val notes: String = "",
)

@Entity(tableName = "sessions", indices = [Index("venueId"), Index("date")])
data class SessionEntity(
    @PrimaryKey val id: String,
    val venueId: String,
    val sport: String,
    val date: String, // YYYY-MM-DD
    val startTime: String, // HH:MM
    val endTime: String, // HH:MM
    val durationMinutes: Int = 60,
    val capacity: Int = 10,
    val bookedCount: Int = 0,
    val price: Double = 800.0,
    val status: String = "AVAILABLE", // AVAILABLE, BOOKED, BLOCKED, MAINTENANCE
    val createdAt: Long = System.currentTimeMillis(),
    val coverImage: String = "",
)

@Entity(tableName = "bookings", indices = [Index("playerId"), Index("venueId"), Index("sessionId")])
data class BookingEntity(
    @PrimaryKey val id: String,
    val bookingReference: String, // BK-20261012-0042
    val playerId: String,
    val playerName: String,
    val playerEmail: String,
    val playerPhone: String,
    val playerCount: Int = 2,
    val venueId: String,
    val venueName: String,
    val sessionId: String,
    val sport: String,
    val date: String,
    val startTime: String,
    val endTime: String,
    val price: Double = 800.0,
    val equipmentRequested: String = "",
    val notes: String = "",
    val status: String = "PENDING", // PENDING, CONFIRMED, REJECTED, CANCELLED, COMPLETED, EXPIRED
    val rejectionReason: String = "",
    val cancellationReason: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "notifications", indices = [Index("userId")])
data class NotificationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val message: String,
    val type: String, // BOOKING_REQUEST, BOOKING_CONFIRMED, BOOKING_REJECTED, BOOKING_CANCELLED, CONFLICT, SCORE_UPDATE
    val targetEntityId: String = "",
    val isRead: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)

@Entity(tableName = "reviews", indices = [Index("venueId"), Index("bookingId")])
data class ReviewEntity(
    @PrimaryKey val id: String,
    val venueId: String,
    val bookingId: String,
    val playerId: String,
    val playerName: String,
    val rating: Int = 5,
    val comment: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "match_scoreboards", indices = [Index("bookingId"), Index("venueId")])
data class MatchScoreboardEntity(
    @PrimaryKey val id: String,
    val bookingId: String,
    val venueId: String,
    val sport: String,
    val teamA: String,
    val teamB: String,
    val scoreA: Int = 0,
    val scoreB: Int = 0,
    val detailText: String = "", // e.g., Cricket: "142/3 (18.2 ov)", Football: "2 - 1 (68')"
    val status: String = "LIVE", // SCHEDULED, LIVE, PAUSED, COMPLETED
    val controllerUserId: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "scoreboard_events", indices = [Index("matchId")])
data class ScoreboardEventEntity(
    @PrimaryKey val id: String,
    val matchId: String,
    val timestampText: String,
    val eventType: String, // Goal, Wicket, Run, Card, Point
    val description: String,
    val createdAt: Long = System.currentTimeMillis(),
)

// Legacy / Tournament Entities
@Entity(tableName = "tournaments")
data class TournamentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sport: String,
    val format: String,
    val venue: String,
    val announcement: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "teams", indices = [Index("tournamentId")])
data class TeamEntity(
    @PrimaryKey val id: String,
    val tournamentId: String,
    val name: String,
    val shortName: String,
    val color: String,
    val seed: Int,
)

@Entity(tableName = "players", indices = [Index("teamId")])
data class PlayerEntity(
    @PrimaryKey val id: String,
    val teamId: String,
    val name: String,
    val number: Int,
    val position: String,
)

@Entity(tableName = "player_stats", indices = [Index("playerId"), Index("teamId")])
data class PlayerStatsEntity(
    @PrimaryKey val playerId: String,
    val teamId: String,
    val tournamentId: String,
    val appearances: Int = 0,
    val goals: Int = 0,
    val assists: Int = 0,
    val yellowCards: Int = 0,
    val redCards: Int = 0,
)

@Entity(tableName = "fixtures", indices = [Index("tournamentId"), Index("round")])
data class FixtureEntity(
    @PrimaryKey val id: String,
    val tournamentId: String,
    val round: String,
    val homeTeamId: String?,
    val awayTeamId: String?,
    val kickoffAt: Long,
    val venue: String,
    val status: String = "SCHEDULED",
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val version: Long = 0,
    val bracketSide: String = "",
    val gameNumber: Int = 0,
    val homeSourceId: String? = null,
    val awaySourceId: String? = null,
    val homeSourceOutcome: String = "WINNER",
    val awaySourceOutcome: String = "WINNER",
    val homeSourceResolved: Boolean = true,
    val awaySourceResolved: Boolean = true,
)

@Entity(tableName = "substitution_requests", indices = [Index("teamId")])
data class SubstitutionRequestEntity(
    @PrimaryKey val id: String,
    val tournamentId: String,
    val teamId: String,
    val playerOut: String,
    val playerIn: String,
    val reason: String,
    val state: String = "PENDING",
    val createdAt: Long = System.currentTimeMillis(),
)
