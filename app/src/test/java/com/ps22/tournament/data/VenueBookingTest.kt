package com.ps22.tournament.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VenueBookingTest {

    @Test
    fun bookingEntityInitializesPendingState() {
        val booking = BookingEntity(
            id = "BKG-01",
            bookingReference = "BK-20261012-0042",
            playerId = "usr_p1",
            playerName = "Rahul",
            playerEmail = "rahul@test.com",
            playerPhone = "9876543210",
            venueId = "VEN-01",
            venueName = "Premier Turf",
            sessionId = "SES-01",
            sport = "Football",
            date = "2026-10-12",
            startTime = "19:00",
            endTime = "20:00",
            status = "PENDING",
        )

        assertEquals("PENDING", booking.status)
        assertEquals("BK-20261012-0042", booking.bookingReference)
        assertEquals("Football", booking.sport)
    }

    @Test
    fun venueEntityFacilitiesAndSportsParsing() {
        val venue = VenueEntity(
            id = "VEN-01",
            managerId = "mgr_01",
            name = "Apex Arena",
            venueType = "Sports Turf",
            supportedSports = "Football, Cricket, Badminton",
            description = "Premium indoor turf",
            phone = "9876543210",
            email = "contact@apex.com",
            address = "Road 10",
            area = "Jubilee Hills",
            city = "Hyderabad",
            operatingHours = "Mon-Sun: 6:00 AM - 11:00 PM",
            facilities = "Parking, Changing Room, Washroom, Drinking Water",
            rules = "Sports shoes mandatory",
            cancellationPolicy = "Free cancellation 4h prior",
            coverImage = "turf_football",
            images = "turf_football",
        )

        val sportsList = venue.supportedSports.split(",").map { it.trim() }
        assertEquals(3, sportsList.size)
        assertTrue(sportsList.contains("Football"))
        assertTrue(sportsList.contains("Cricket"))

        val facilitiesList = venue.facilities.split(",").map { it.trim() }
        assertEquals(4, facilitiesList.size)
        assertTrue(facilitiesList.contains("Parking"))
    }

    @Test
    fun scoreboardEntitySportDetails() {
        val scoreboard = MatchScoreboardEntity(
            id = "MATCH-01",
            bookingId = "BKG-01",
            venueId = "VEN-01",
            sport = "Cricket",
            teamA = "Strikers",
            teamB = "Warriors",
            scoreA = 142,
            scoreB = 110,
            detailText = "142/3 (18.2 ov)",
            status = "LIVE",
            controllerUserId = "usr_p1",
        )

        assertEquals("Cricket", scoreboard.sport)
        assertEquals("LIVE", scoreboard.status)
        assertEquals(142, scoreboard.scoreA)
        assertEquals("142/3 (18.2 ov)", scoreboard.detailText)
    }

    @Test
    fun scoreboardControllerPermissionValidation() {
        val bookingPlayerId = "usr_p1"
        val spectatorUserId = "usr_p99"

        val scoreboard = MatchScoreboardEntity(
            id = "MATCH-01",
            bookingId = "BKG-01",
            venueId = "VEN-01",
            sport = "Football",
            teamA = "Rahul FC",
            teamB = "Opponents",
            scoreA = 0,
            scoreB = 0,
            detailText = "0 - 0",
            status = "LIVE",
            controllerUserId = bookingPlayerId,
        )

        // Booked player has scorer permission
        val canBookedPlayerControl = scoreboard.controllerUserId == bookingPlayerId
        assertTrue(canBookedPlayerControl)

        // Spectator does NOT have scorer permission
        val canSpectatorControl = scoreboard.controllerUserId == spectatorUserId
        assertFalse(canSpectatorControl)
    }
}
