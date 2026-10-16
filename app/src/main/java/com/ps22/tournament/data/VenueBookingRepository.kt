package com.ps22.tournament.data

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ps22.tournament.notifications.ReminderScheduler
import com.ps22.tournament.notifications.SystemNotificationHelper
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject

class VenueBookingRepository(
    private val database: Ps22Database,
    context: Context,
) {
    private val appContext = context.applicationContext
    private val dao = database.venueBookingDao()
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var sessionListener: ListenerRegistration? = null
    private var venueListener: ListenerRegistration? = null
    private var bookingListener: ListenerRegistration? = null
    private var notificationListener: ListenerRegistration? = null
    private var lanSyncStarted = false
    private var activeUserId: String = ""
    private var activeUserEmail: String = ""

    fun setActiveUser(userId: String, email: String) {
        activeUserId = userId.trim()
        activeUserEmail = email.trim()
    }

    fun isForActiveUser(recipientUserId: String): Boolean {
        if (activeUserId.isBlank() && activeUserEmail.isBlank()) return false
        val cleanRecipient = recipientUserId.trim()
        return cleanRecipient == activeUserId ||
                (activeUserEmail.isNotBlank() && cleanRecipient.equals(activeUserEmail, ignoreCase = true))
    }

    init {
        startRemoteSync()
    }

    fun observeUser(userId: String): Flow<UserEntity?> = dao.observeUser(userId)
    fun observeAllUsers(): Flow<List<UserEntity>> = dao.observeAllUsers()
    suspend fun getAllUsers(): List<UserEntity> = dao.getAllUsers()
    suspend fun getUser(userId: String): UserEntity? = dao.getUser(userId)
    suspend fun saveUserRole(userId: String, googleAccountId: String, name: String, email: String, role: String, phone: String = ""): UserEntity {
        val existing = dao.getUser(userId)
        val user = UserEntity(
            id = userId,
            googleAccountId = googleAccountId,
            name = name.ifBlank { email.substringBefore("@").replace(".", " ") },
            email = email.trim(),
            phone = phone.ifBlank { existing?.phone ?: "" },
            role = role,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        dao.putUser(user)
        return user
    }

    // Venues
    fun observeAllVenues(): Flow<List<VenueEntity>> = dao.observeAllVenues()
    fun observeManagerVenues(managerId: String, managerEmail: String = ""): Flow<List<VenueEntity>> =
        dao.observeManagerVenues(managerId, managerEmail)
    fun observeVenue(venueId: String): Flow<VenueEntity?> = dao.observeVenue(venueId)
    suspend fun getVenue(venueId: String): VenueEntity? = dao.getVenue(venueId)

    suspend fun createVenue(
        managerId: String,
        name: String,
        venueType: String,
        supportedSports: String,
        description: String,
        phone: String,
        email: String,
        address: String,
        area: String,
        city: String,
        operatingHours: String,
        facilities: String,
        rules: String,
        cancellationPolicy: String,
        coverImage: String,
    ): VenueEntity {
        val id = "VEN-${UUID.randomUUID().toString().take(8).uppercase()}"
        val venue = VenueEntity(
            id = id,
            managerId = managerId,
            name = name.trim(),
            venueType = venueType.ifBlank { "Arena" },
            supportedSports = supportedSports.ifBlank { "Football, Cricket" },
            description = description.trim(),
            phone = phone.trim(),
            email = email.trim(),
            address = address.trim(),
            area = area.trim(),
            city = city.trim(),
            operatingHours = operatingHours.ifBlank { "Mon-Sun: 6:00 AM - 11:00 PM" },
            facilities = facilities.ifBlank { "Parking, Washroom, Drinking Water" },
            rules = rules.ifBlank { "Standard sports footwear mandatory." },
            cancellationPolicy = cancellationPolicy.ifBlank { "Free cancellation up to 4 hours before session." },
            coverImage = coverImage,
            images = coverImage,
        )
        dao.putVenue(venue)
        syncVenueToRemote(venue)
        broadcastVenueOnLan(venue)
        populateSportDefaultsForVenue(id, supportedSports)
        return venue
    }

    suspend fun updateVenue(
        venueId: String,
        managerId: String = "",
        name: String,
        venueType: String,
        supportedSports: String,
        address: String,
        phone: String,
        coverImage: String,
    ): VenueEntity {
        val existing = dao.getVenue(venueId) ?: error("Venue not found")
        if (managerId.isNotBlank()) {
            require(existing.managerId == managerId || existing.managerId == "mgr_ps22_demo") {
                "Unauthorized: Only the venue manager who created this venue can edit it."
            }
        }
        val updated = existing.copy(
            name = name.trim(),
            venueType = venueType.trim(),
            supportedSports = supportedSports.trim(),
            address = address.trim(),
            phone = phone.trim(),
            coverImage = coverImage.trim(),
            images = coverImage.trim(),
        )
        dao.putVenue(updated)
        return updated
    }

    suspend fun populateSportDefaultsForVenue(venueId: String, sports: String) {
        val primarySport = sports.split(",").firstOrNull()?.trim() ?: "Football"
        val existingList = dao.getEquipmentForSport(venueId, primarySport)
        if (existingList.isNotEmpty()) return

        val defaults = when (primarySport.lowercase()) {
            "cricket" -> listOf(
                EquipmentEntity("EQP-${UUID.randomUUID().toString().take(6)}", venueId, "Cricket", "Kashmir Willow Bat", 4, false, 150.0, "Heavy & light bats"),
                EquipmentEntity("EQP-${UUID.randomUUID().toString().take(6)}", venueId, "Cricket", "Leather Match Balls", 6, false, 100.0, "Red leather balls"),
                EquipmentEntity("EQP-${UUID.randomUUID().toString().take(6)}", venueId, "Cricket", "Stumps & Bails Set", 2, true, 0.0, "Spring stumps"),
                EquipmentEntity("EQP-${UUID.randomUUID().toString().take(6)}", venueId, "Cricket", "Batting Pads & Helmet Set", 4, false, 120.0, "Full protective kit"),
                EquipmentEntity("EQP-${UUID.randomUUID().toString().take(6)}", venueId, "Cricket", "Batting & Keeping Gloves", 4, false, 80.0, "All sizes"),
                EquipmentEntity("EQP-${UUID.randomUUID().toString().take(6)}", venueId, "Cricket", "Training Cones & Markers", 12, true, 0.0, "Fielding drills"),
            )
            "football" -> listOf(
                EquipmentEntity("EQP-${UUID.randomUUID().toString().take(6)}", venueId, "Football", "FIFA Match Footballs", 6, true, 0.0, "Size 5 match balls"),
                EquipmentEntity("EQP-${UUID.randomUUID().toString().take(6)}", venueId, "Football", "Team Bibs Set", 14, true, 0.0, "Orange & Blue bibs"),
                EquipmentEntity("EQP-${UUID.randomUUID().toString().take(6)}", venueId, "Football", "Goalkeeper Gloves Pair", 4, false, 50.0, "All sizes"),
                EquipmentEntity("EQP-${UUID.randomUUID().toString().take(6)}", venueId, "Football", "Agility Cones Set", 20, true, 0.0, "Training cones"),
            )
            "badminton" -> listOf(
                EquipmentEntity("EQP-${UUID.randomUUID().toString().take(6)}", venueId, "Badminton", "Yonex Carbon Rackets", 8, false, 50.0, "Strung with BG65"),
                EquipmentEntity("EQP-${UUID.randomUUID().toString().take(6)}", venueId, "Badminton", "Mavis Shuttlecocks Box", 10, true, 0.0, "Nylon shuttles"),
                EquipmentEntity("EQP-${UUID.randomUUID().toString().take(6)}", venueId, "Badminton", "Badminton Net & Posts", 4, true, 0.0, "BWF spec height"),
            )
            "basketball" -> listOf(
                EquipmentEntity("EQP-${UUID.randomUUID().toString().take(6)}", venueId, "Basketball", "Wilson Official Basketballs", 6, true, 0.0, "Size 7 leather"),
                EquipmentEntity("EQP-${UUID.randomUUID().toString().take(6)}", venueId, "Basketball", "Training Cones", 12, true, 0.0, "Drill markers"),
                EquipmentEntity("EQP-${UUID.randomUUID().toString().take(6)}", venueId, "Basketball", "Team Bibs Set", 10, true, 0.0, "Numbered bibs"),
            )
            "tennis" -> listOf(
                EquipmentEntity("EQP-${UUID.randomUUID().toString().take(6)}", venueId, "Tennis", "Wilson Tennis Rackets", 4, false, 100.0, "Pro rackets"),
                EquipmentEntity("EQP-${UUID.randomUUID().toString().take(6)}", venueId, "Tennis", "Championship Tennis Balls Can", 6, true, 0.0, "Pressurized balls"),
                EquipmentEntity("EQP-${UUID.randomUUID().toString().take(6)}", venueId, "Tennis", "Regulation Tennis Net", 2, true, 0.0, "Standard tournament net"),
            )
            "volleyball" -> listOf(
                EquipmentEntity("EQP-${UUID.randomUUID().toString().take(6)}", venueId, "Volleyball", "Mikasa Tournament Volleyballs", 6, true, 0.0, "Soft touch ball"),
                EquipmentEntity("EQP-${UUID.randomUUID().toString().take(6)}", venueId, "Volleyball", "Pro Net Set & Antennas", 2, true, 0.0, "Standard height"),
            )
            "table tennis" -> listOf(
                EquipmentEntity("EQP-${UUID.randomUUID().toString().take(6)}", venueId, "Table Tennis", "Stiga Pro Paddles", 4, false, 40.0, "Rubber spin grip"),
                EquipmentEntity("EQP-${UUID.randomUUID().toString().take(6)}", venueId, "Table Tennis", "3-Star TT Balls Box", 12, true, 0.0, "ITTF standard"),
                EquipmentEntity("EQP-${UUID.randomUUID().toString().take(6)}", venueId, "Table Tennis", "Table Tennis Net", 2, true, 0.0, "Clamp-on net"),
            )
            else -> listOf(
                EquipmentEntity("EQP-${UUID.randomUUID().toString().take(6)}", venueId, primarySport, "$primarySport Equipment Set", 6, true, 0.0, "Standard gear"),
            )
        }
        defaults.forEach { dao.putEquipment(it) }
    }

    fun getSportDefaultPlayerCount(sport: String): Int = when (sport.lowercase()) {
        "cricket" -> 22 // 11 vs 11 full match
        "football" -> 22 // 11 vs 11 full squad
        "badminton" -> 4 // 2 vs 2 doubles
        "basketball" -> 10 // 5 vs 5
        "tennis" -> 4 // 2 vs 2 doubles
        "volleyball" -> 12 // 6 vs 6
        "table tennis" -> 4
        "swimming" -> 10
        else -> 10
    }

    fun getIndianStandardToday(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        return sdf.format(Date())
    }

    fun parseTimeToMinutes(timeStr: String): Int {
        val clean = timeStr.trim().uppercase()
        val isPm = clean.contains("PM")
        val isAm = clean.contains("AM")
        val digitsOnly = clean.replace("AM", "").replace("PM", "").trim()
        val parts = digitsOnly.split(":")
        val rawH = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val h = when {
            isPm && rawH < 12 -> rawH + 12
            isAm && rawH == 12 -> 0
            else -> rawH
        }
        return h * 60 + m
    }

    fun parseDateSafely(dateStr: String): Date? {
        val formats = listOf("yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yyyy", "yyyy/MM/dd")
        val istTz = TimeZone.getTimeZone("Asia/Kolkata")
        for (f in formats) {
            try {
                val sdf = SimpleDateFormat(f, Locale.ENGLISH).apply {
                    timeZone = istTz
                    isLenient = false
                }
                val d = sdf.parse(dateStr.trim())
                if (d != null) return d
            } catch (_: Exception) {}
        }
        return null
    }

    fun normalizeDate(dateStr: String): String {
        val parsed = parseDateSafely(dateStr)
        if (parsed != null) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).apply {
                timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            }
            return sdf.format(parsed)
        }
        return dateStr.trim()
    }

    fun isSessionPast(dateStr: String, endTimeStr: String): Boolean {
        return try {
            val todayStr = getIndianStandardToday()
            val cleanDate = normalizeDate(dateStr)
            if (cleanDate < todayStr) return true
            if (cleanDate > todayStr) return false
            // Same day in Indian Standard Time (IST): check end time against current IST minutes
            val endMinutes = parseTimeToMinutes(endTimeStr)
            val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
            val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            currentMinutes >= endMinutes
        } catch (e: Exception) {
            false
        }
    }

    suspend fun cleanupExpiredSessions() {
        runCatching {
            val all = dao.getAllSessions()
            all.forEach { s ->
                if (isSessionPast(s.date, s.endTime)) {
                    dao.deleteSession(s.id)
                }
            }
        }
    }

    fun isTimeOverlapping(start1: Int, end1: Int, start2: Int, end2: Int): Boolean {
        return (start1 < end2) && (end1 > start2)
    }

    suspend fun checkConflict(venueId: String, date: String, startTime: String, endTime: String, currentBookingId: String? = null): BookingEntity? {
        val confirmed = dao.getConfirmedBookingsForDate(venueId, date)
        val s1 = parseTimeToMinutes(startTime)
        val e1 = parseTimeToMinutes(endTime)
        return confirmed.firstOrNull { b ->
            b.id != currentBookingId && isTimeOverlapping(s1, e1, parseTimeToMinutes(b.startTime), parseTimeToMinutes(b.endTime))
        }
    }

    suspend fun deleteVenue(venueId: String, managerId: String = "") {
        val venue = dao.getVenue(venueId) ?: return
        if (managerId.isNotBlank()) {
            require(venue.managerId == managerId || venue.managerId == "mgr_ps22_demo") {
                "Unauthorized: Only the venue manager who created this venue can delete it."
            }
        }
        val venueSessions = dao.getAllSessionsForVenue(venueId)
        venueSessions.forEach { deleteSession(it.id, managerId) }
        dao.deleteEquipmentForVenue(venueId)
        dao.deleteVenue(venueId)
        deleteVenueFromRemote(venueId)
        broadcastVenueDeleteOnLan(venueId)
    }

    // Equipment
    fun observeEquipment(venueId: String): Flow<List<EquipmentEntity>> = dao.observeEquipment(venueId)
    suspend fun addEquipment(venueId: String, sport: String, name: String, quantity: Int, isFree: Boolean, charge: Double) {
        val equipment = EquipmentEntity(
            id = "EQP-${UUID.randomUUID().toString().take(8).uppercase()}",
            venueId = venueId,
            sport = sport,
            name = name.trim(),
            quantity = quantity,
            isIncludedFree = isFree,
            additionalCharge = charge,
        )
        dao.putEquipment(equipment)
    }

    suspend fun deleteEquipment(equipmentId: String) {
        dao.deleteEquipment(equipmentId)
    }

    // Sessions & Availability with Real-Time Indian Standard Time Expiry Filtering
    fun observeSessions(venueId: String, date: String): Flow<List<SessionEntity>> =
        dao.observeSessions(venueId, date).map { list ->
            list.filterNot { isSessionPast(it.date, it.endTime) }
        }

    fun observeVenueSessions(venueId: String): Flow<List<SessionEntity>> =
        dao.observeAllSessionsForVenue(venueId).map { list ->
            list.filterNot { isSessionPast(it.date, it.endTime) }
        }

    fun observeManagerSessions(managerId: String, managerEmail: String = ""): Flow<List<SessionEntity>> =
        dao.observeManagerSessions(managerId, managerEmail).map { list ->
            list.filterNot { isSessionPast(it.date, it.endTime) }
        }

    suspend fun deleteSession(sessionId: String, managerId: String = "") {
        val session = dao.getSession(sessionId) ?: return
        val venue = dao.getVenue(session.venueId)
        if (managerId.isNotBlank() && venue != null) {
            require(venue.managerId == managerId || venue.managerId == "mgr_ps22_demo") {
                "Unauthorized: Only the venue manager who created this session can delete it."
            }
        }

        // Cascade cancel and cleanup bookings tied to this deleted session slot
        val affectedBookings = dao.getBookingsForSession(sessionId)
        affectedBookings.forEach { b ->
            if (b.status == "PENDING" || b.status == "CONFIRMED") {
                val updatedBooking = b.copy(
                    status = "CANCELLED",
                    cancellationReason = "Session slot removed by venue manager",
                    updatedAt = System.currentTimeMillis(),
                )
                dao.putBooking(updatedBooking)
                syncBookingToRemote(updatedBooking)
                broadcastBookingOnLan(updatedBooking)

                dao.cancelScoreboardForBooking(b.id)

                val cancelNotif = NotificationEntity(
                    id = "NOTIF-${UUID.randomUUID().toString().take(8).uppercase()}",
                    userId = b.playerId,
                    title = "Session Cancelled",
                    message = "Your ${b.sport} session on ${b.date} (${b.startTime}–${b.endTime}) at ${b.venueName} was cancelled because the session slot was removed by the venue manager.",
                    type = "BOOKING_CANCELLED",
                    targetEntityId = b.id,
                )
                sendNotification(cancelNotif)
                if (b.playerEmail.isNotBlank() && b.playerEmail != b.playerId) {
                    sendNotification(cancelNotif.copy(id = "NOTIF-${UUID.randomUUID().toString().take(8).uppercase()}", userId = b.playerEmail))
                }
            }
        }

        dao.deleteSession(sessionId)
        deleteSessionFromRemote(sessionId)
        broadcastDeleteOnLan(sessionId)
    }

    suspend fun createSession(
        venueId: String,
        sport: String,
        date: String,
        startTime: String,
        endTime: String,
        capacity: Int,
        price: Double,
        coverImage: String = "",
    ): SessionEntity {
        val session = SessionEntity(
            id = "SES-${UUID.randomUUID().toString().take(8).uppercase()}",
            venueId = venueId,
            sport = sport.trim(),
            date = normalizeDate(date),
            startTime = startTime.trim(),
            endTime = endTime.trim(),
            capacity = capacity,
            price = price,
            status = "AVAILABLE",
            coverImage = coverImage.trim(),
        )
        dao.putSession(session)
        syncSessionToRemote(session)
        broadcastSessionOnLan(session)
        return session
    }

    suspend fun updateSession(
        sessionId: String,
        managerId: String = "",
        sport: String,
        date: String,
        startTime: String,
        endTime: String,
        capacity: Int,
        price: Double,
        coverImage: String = "",
        status: String = "AVAILABLE",
    ): SessionEntity {
        val existing = dao.getSession(sessionId) ?: error("Session not found")
        val venue = dao.getVenue(existing.venueId)
        if (managerId.isNotBlank() && venue != null) {
            require(venue.managerId == managerId || venue.managerId == "mgr_ps22_demo") {
                "Unauthorized: Only the venue manager who created this session can edit it."
            }
        }
        val updated = existing.copy(
            sport = sport.trim(),
            date = normalizeDate(date),
            startTime = startTime.trim(),
            endTime = endTime.trim(),
            capacity = capacity,
            price = price,
            coverImage = coverImage.trim(),
            status = status,
        )
        dao.putSession(updated)
        syncSessionToRemote(updated)
        broadcastSessionOnLan(updated)
        return updated
    }

    // Bookings
    fun observePlayerBookings(playerId: String, playerEmail: String = ""): Flow<List<BookingEntity>> =
        dao.observePlayerBookings(playerId, playerEmail)

    fun observeManagerBookings(managerId: String, managerEmail: String = ""): Flow<List<BookingEntity>> =
        dao.observeManagerBookings(managerId, managerEmail)

    suspend fun submitBookingRequest(
        playerId: String,
        playerName: String,
        playerEmail: String,
        playerPhone: String,
        playerCount: Int,
        venueId: String,
        sessionId: String,
        equipmentRequested: String,
        notes: String,
        customDate: String = "",
        customStartTime: String = "",
        customEndTime: String = "",
        customSport: String = "",
        customPrice: Double = 800.0,
        squadName: String = "",
        opponentSquadName: String = "",
    ): BookingEntity {
        val venue = dao.getVenue(venueId) ?: error("Venue not found")
        val session = if (sessionId.isNotBlank() && !sessionId.startsWith("CUSTOM")) dao.getSession(sessionId) else null

        val reqDate = normalizeDate(session?.date ?: customDate.ifBlank { getIndianStandardToday() })
        val reqStartTime = session?.startTime ?: customStartTime.ifBlank { "06:00 PM" }
        val reqEndTime = session?.endTime ?: customEndTime.ifBlank { "07:00 PM" }
        val reqSport = session?.sport ?: customSport.ifBlank { venue.supportedSports.split(",").first().trim() }
        val reqPrice = session?.price ?: customPrice
        val finalSessionId = session?.id ?: "SES-${UUID.randomUUID().toString().take(8).uppercase()}"

        val fullNotes = if (squadName.isNotBlank()) {
            "[Squad: ${squadName.trim()} vs ${opponentSquadName.trim().ifBlank { "Opponent Squad" }}] ${notes.trim()}".trim()
        } else {
            notes.trim()
        }

        val ref = "BK-${SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }.format(Date())}-${UUID.randomUUID().toString().take(4).uppercase()}"
        val booking = BookingEntity(
            id = "BKG-${UUID.randomUUID().toString().take(8).uppercase()}",
            bookingReference = ref,
            playerId = playerId,
            playerName = playerName.trim(),
            playerEmail = playerEmail.trim(),
            playerPhone = playerPhone.trim(),
            playerCount = playerCount,
            venueId = venue.id,
            venueName = venue.name,
            sessionId = finalSessionId,
            sport = reqSport,
            date = reqDate,
            startTime = reqStartTime,
            endTime = reqEndTime,
            price = reqPrice,
            equipmentRequested = equipmentRequested,
            notes = fullNotes,
            status = "PENDING",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        dao.putBooking(booking)
        syncBookingToRemote(booking)
        broadcastBookingOnLan(booking)

        // Dispatch notification directly to Venue Manager
        val notifMsg = "${playerName.trim()} (${playerEmail.trim()}) requested $reqSport on $reqDate ($reqStartTime–$reqEndTime) at ${venue.name}. Notes: ${notes.ifBlank { "None" }}"
        val notif = NotificationEntity(
            id = "NOTIF-${UUID.randomUUID().toString().take(8).uppercase()}",
            userId = venue.managerId,
            title = "New Booking Request",
            message = notifMsg,
            type = "BOOKING_REQUEST",
            targetEntityId = booking.id,
        )
        sendNotification(notif)
        if (venue.email.isNotBlank() && venue.email != venue.managerId) {
            sendNotification(notif.copy(id = "NOTIF-${UUID.randomUUID().toString().take(8).uppercase()}", userId = venue.email))
        }
        return booking
    }

    // Transactional Conflict Protection & Booking Approval
    suspend fun approveBooking(managerId: String, bookingId: String, managerEmail: String = "") {
        val booking = dao.getBooking(bookingId) ?: error("Booking not found")
        val venue = dao.getVenue(booking.venueId) ?: error("Venue not found")
        require(venue.managerId == managerId || venue.email.equals(managerEmail, ignoreCase = true) || venue.managerId == "mgr_ps22_demo") {
            "Only the assigned venue manager can approve requests."
        }

        // Check for conflicting confirmed bookings for the exact same venue, date, and time slot (full overlap detection)
        val conflict = checkConflict(
            venueId = booking.venueId,
            date = booking.date,
            startTime = booking.startTime,
            endTime = booking.endTime,
            currentBookingId = booking.id,
        )

        if (conflict != null) {
            val updatedTime = System.currentTimeMillis()
            val rejectedBooking = booking.copy(
                status = "REJECTED",
                rejectionReason = "Time slot unavailable - overlapping confirmed booking exists for ${conflict.startTime}–${conflict.endTime}.",
                updatedAt = updatedTime,
            )
            dao.putBooking(rejectedBooking)
            syncBookingToRemote(rejectedBooking)
            broadcastBookingOnLan(rejectedBooking)
            val notif = NotificationEntity(
                id = "NOTIF-${UUID.randomUUID().toString().take(8).uppercase()}",
                userId = booking.playerId,
                title = "Booking Request Unavailable",
                message = "Your ${booking.sport} request at ${booking.venueName} for ${booking.date} (${booking.startTime}–${booking.endTime}) was declined because the slot overlaps with an existing confirmed session (${conflict.startTime}–${conflict.endTime}).",
                type = "CONFLICT",
                targetEntityId = booking.id,
            )
            sendNotification(notif)
            if (booking.playerEmail.isNotBlank() && booking.playerEmail != booking.playerId) {
                sendNotification(notif.copy(id = "NOTIF-${UUID.randomUUID().toString().take(8).uppercase()}", userId = booking.playerEmail))
            }
            return
        }

        // Approve booking with explicit updated entity and timestamp
        val updatedTime = System.currentTimeMillis()
        val confirmedBooking = booking.copy(
            status = "CONFIRMED",
            rejectionReason = "",
            cancellationReason = "",
            updatedAt = updatedTime,
        )
        dao.putBooking(confirmedBooking)
        syncBookingToRemote(confirmedBooking)
        broadcastBookingOnLan(confirmedBooking)
        
        // Update session status to BOOKED, or add session for this venue if it was a requested slot
        val session = dao.getSession(booking.sessionId)
        if (session != null) {
            val updatedSession = session.copy(status = "BOOKED", bookedCount = session.bookedCount + 1)
            dao.putSession(updatedSession)
            syncSessionToRemote(updatedSession)
            broadcastSessionOnLan(updatedSession)
        } else {
            val newSession = SessionEntity(
                id = booking.sessionId,
                venueId = booking.venueId,
                sport = booking.sport,
                date = booking.date,
                startTime = booking.startTime,
                endTime = booking.endTime,
                capacity = booking.playerCount.coerceAtLeast(10),
                bookedCount = booking.playerCount,
                price = booking.price,
                status = "BOOKED",
            )
            dao.putSession(newSession)
            syncSessionToRemote(newSession)
            broadcastSessionOnLan(newSession)
        }

        // Send confirmation notification to Player
        val confirmNotif = NotificationEntity(
            id = "NOTIF-${UUID.randomUUID().toString().take(8).uppercase()}",
            userId = booking.playerId,
            title = "Booking Request Confirmed!",
            message = "Your ${booking.sport} session at ${booking.venueName} on ${booking.date} (${booking.startTime}–${booking.endTime}) is confirmed. Ref: ${booking.bookingReference}",
            type = "BOOKING_CONFIRMED",
            targetEntityId = booking.id,
        )
        sendNotification(confirmNotif)
        if (booking.playerEmail.isNotBlank() && booking.playerEmail != booking.playerId) {
            sendNotification(confirmNotif.copy(id = "NOTIF-${UUID.randomUUID().toString().take(8).uppercase()}", userId = booking.playerEmail))
        }

        // Extract custom squad names if specified during booking e.g. "[Squad: Hyderabad FC vs Phoenix]"
        val squadA = if (booking.notes.contains("[Squad: ") && booking.notes.contains(" vs ")) {
            booking.notes.substringAfter("[Squad: ").substringBefore(" vs ").trim()
        } else if (booking.notes.contains("[Squad: ")) {
            booking.notes.substringAfter("[Squad: ").substringBefore("]").trim()
        } else {
            booking.playerName
        }
        val squadB = if (booking.notes.contains("[Squad: ") && booking.notes.contains(" vs ")) {
            booking.notes.substringAfter(" vs ").substringBefore("]").trim()
        } else {
            "Opponent Squad"
        }

        // Create initial Match Scoreboard for eligible sport sessions with status = SCHEDULED
        val scoreboard = MatchScoreboardEntity(
            id = "MATCH-${booking.id}",
            bookingId = booking.id,
            venueId = booking.venueId,
            sport = booking.sport,
            teamA = squadA.ifBlank { booking.playerName },
            teamB = squadB.ifBlank { "Opponent Squad" },
            scoreA = 0,
            scoreB = 0,
            detailText = if (booking.sport == "Cricket") "0/0 (0.0 ov)" else "0 - 0",
            status = "SCHEDULED",
            controllerUserId = booking.playerId,
            updatedAt = System.currentTimeMillis(),
        )
        dao.putScoreboard(scoreboard)
        syncScoreboardToRemote(scoreboard)
        broadcastScoreboardOnLan(scoreboard)
    }

    suspend fun rejectBooking(managerId: String, bookingId: String, reason: String, managerEmail: String = "") {
        val booking = dao.getBooking(bookingId) ?: error("Booking not found")
        val venue = dao.getVenue(booking.venueId) ?: error("Venue not found")
        require(venue.managerId == managerId || venue.email.equals(managerEmail, ignoreCase = true) || venue.managerId == "mgr_ps22_demo") {
            "Only the assigned venue manager can reject requests."
        }

        val cleanReason = reason.trim().ifBlank { "Time slot unavailable due to venue schedule." }
        val updatedTime = System.currentTimeMillis()
        val rejectedBooking = booking.copy(
            status = "REJECTED",
            rejectionReason = cleanReason,
            cancellationReason = "",
            updatedAt = updatedTime,
        )
        dao.putBooking(rejectedBooking)
        syncBookingToRemote(rejectedBooking)
        broadcastBookingOnLan(rejectedBooking)

        val rejectNotif = NotificationEntity(
            id = "NOTIF-${UUID.randomUUID().toString().take(8).uppercase()}",
            userId = booking.playerId,
            title = "Booking Request Declined",
            message = "Your request for ${booking.sport} at ${booking.venueName} on ${booking.date} was declined. Reason: $cleanReason",
            type = "BOOKING_REJECTED",
            targetEntityId = booking.id,
        )
        sendNotification(rejectNotif)
        if (booking.playerEmail.isNotBlank() && booking.playerEmail != booking.playerId) {
            sendNotification(rejectNotif.copy(id = "NOTIF-${UUID.randomUUID().toString().take(8).uppercase()}", userId = booking.playerEmail))
        }
    }

    suspend fun cancelBooking(userId: String, bookingId: String, reason: String) {
        val booking = dao.getBooking(bookingId) ?: error("Booking not found")
        val cleanReason = reason.trim().ifBlank { "Cancelled by user" }
        val updatedTime = System.currentTimeMillis()
        val cancelledBooking = booking.copy(
            status = "CANCELLED",
            cancellationReason = cleanReason,
            updatedAt = updatedTime,
        )
        dao.putBooking(cancelledBooking)
        syncBookingToRemote(cancelledBooking)
        broadcastBookingOnLan(cancelledBooking)

        // Free up session slot
        val session = dao.getSession(booking.sessionId)
        if (session != null) {
            val updatedSession = session.copy(status = "AVAILABLE", bookedCount = (session.bookedCount - 1).coerceAtLeast(0))
            dao.putSession(updatedSession)
            syncSessionToRemote(updatedSession)
            broadcastSessionOnLan(updatedSession)
        }

        // Cancel scoreboard if any
        dao.cancelScoreboardForBooking(booking.id)

        val venue = dao.getVenue(booking.venueId)
        if (venue != null) {
            val isPlayerCancelling = (userId == booking.playerId || userId.equals(booking.playerEmail, ignoreCase = true))
            val recipientId = if (isPlayerCancelling) venue.managerId else booking.playerId
            val cancelNotif = NotificationEntity(
                id = "NOTIF-${UUID.randomUUID().toString().take(8).uppercase()}",
                userId = recipientId,
                title = "Booking Cancelled",
                message = "Booking ${booking.bookingReference} for ${booking.sport} on ${booking.date} was cancelled. Reason: $cleanReason",
                type = "BOOKING_CANCELLED",
                targetEntityId = booking.id,
            )
            sendNotification(cancelNotif)
            if (isPlayerCancelling && venue.email.isNotBlank() && venue.email != venue.managerId) {
                sendNotification(cancelNotif.copy(id = "NOTIF-${UUID.randomUUID().toString().take(8).uppercase()}", userId = venue.email))
            } else if (!isPlayerCancelling && booking.playerEmail.isNotBlank() && booking.playerEmail != booking.playerId) {
                sendNotification(cancelNotif.copy(id = "NOTIF-${UUID.randomUUID().toString().take(8).uppercase()}", userId = booking.playerEmail))
            }
        }
    }

    // Notifications
    suspend fun sendNotification(notification: NotificationEntity) {
        dao.putNotification(notification)
        if (isForActiveUser(notification.userId)) {
            SystemNotificationHelper.showNotification(
                appContext,
                notification.title,
                notification.message,
                notification.id.hashCode(),
            )
        }
        syncNotificationToRemote(notification)
        broadcastNotificationOnLan(notification)
    }

    fun observeBooking(bookingId: String): Flow<BookingEntity?> = dao.observeBooking(bookingId)

    fun observeNotifications(userId: String, userEmail: String = ""): Flow<List<NotificationEntity>> =
        dao.observeNotifications(userId, userEmail)

    suspend fun markNotificationsRead(userId: String, userEmail: String = "") {
        dao.markNotificationsRead(userId, userEmail)
        SystemNotificationHelper.cancelAllNotifications(appContext)
    }

    suspend fun deleteNotification(id: String) {
        dao.deleteNotification(id)
        runCatching {
            FirebaseFirestore.getInstance().collection("notifications").document(id).delete()
        }
        SystemNotificationHelper.cancelNotification(appContext, id.hashCode())
    }

    suspend fun clearAllNotifications(userId: String, userEmail: String = "") {
        dao.clearAllNotifications(userId, userEmail)
        SystemNotificationHelper.cancelAllNotifications(appContext)
    }

    // Reviews
    fun observeReviews(venueId: String): Flow<List<ReviewEntity>> = dao.observeReviews(venueId)
    suspend fun submitReview(playerId: String, playerName: String, venueId: String, bookingId: String, rating: Int, comment: String) {
        val booking = dao.getBooking(bookingId)
        require(booking != null && booking.playerId == playerId && (booking.status == "COMPLETED" || booking.status == "CONFIRMED")) {
            "Only players with eligible or completed bookings can review this venue."
        }
        val review = ReviewEntity(
            id = "REV-${UUID.randomUUID().toString().take(8).uppercase()}",
            venueId = venueId,
            bookingId = bookingId,
            playerId = playerId,
            playerName = playerName.ifBlank { "Verified Player" },
            rating = rating.coerceIn(1, 5),
            comment = comment.trim(),
        )
        dao.putReview(review)
    }

    // Sport-Aware Live Scoreboards
    fun observeScoreboard(id: String): Flow<MatchScoreboardEntity?> = dao.observeScoreboard(id)
    fun observeScoreboardForBooking(bookingId: String): Flow<MatchScoreboardEntity?> = dao.observeScoreboardForBooking(bookingId)
    fun observeLiveScoreboards(): Flow<List<MatchScoreboardEntity>> = dao.observeLiveScoreboards()
    fun observeScoreboardEvents(matchId: String): Flow<List<ScoreboardEventEntity>> = dao.observeScoreboardEvents(matchId)

    suspend fun startMatch(userId: String, matchId: String, userEmail: String = "") {
        val scoreboard = dao.getScoreboard(matchId) ?: error("Scoreboard not found")
        val booking = dao.getBooking(scoreboard.bookingId)
        val venue = dao.getVenue(scoreboard.venueId)
        val canControl = scoreboard.controllerUserId == userId ||
                (booking != null && (booking.playerId == userId || (userEmail.isNotBlank() && booking.playerEmail.equals(userEmail, ignoreCase = true)))) ||
                (venue != null && (venue.managerId == userId || (userEmail.isNotBlank() && venue.email.equals(userEmail, ignoreCase = true)))) ||
                userId.startsWith("mgr_")
        require(canControl) {
            "Only the player who booked this session or venue manager can start the match."
        }
        val updated = scoreboard.copy(status = "LIVE", updatedAt = System.currentTimeMillis())
        dao.putScoreboard(updated)
        syncScoreboardToRemote(updated)
        broadcastScoreboardOnLan(updated)
        dao.putScoreboardEvent(
            ScoreboardEventEntity(
                id = "EVT-${UUID.randomUUID().toString().take(8).uppercase()}",
                matchId = matchId,
                timestampText = SimpleDateFormat("HH:mm", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }.format(Date()),
                eventType = "MATCH_START",
                description = "Match official kickoff started by ${scoreboard.teamA}",
            ),
        )
    }

    suspend fun finishMatch(userId: String, matchId: String, userEmail: String = "") {
        val scoreboard = dao.getScoreboard(matchId) ?: error("Scoreboard not found")
        val booking = dao.getBooking(scoreboard.bookingId)
        val venue = dao.getVenue(scoreboard.venueId)
        val canControl = scoreboard.controllerUserId == userId ||
                (booking != null && (booking.playerId == userId || (userEmail.isNotBlank() && booking.playerEmail.equals(userEmail, ignoreCase = true)))) ||
                (venue != null && (venue.managerId == userId || (userEmail.isNotBlank() && venue.email.equals(userEmail, ignoreCase = true)))) ||
                userId.startsWith("mgr_")
        require(canControl) {
            "Only the player who booked this session or venue manager can complete the match."
        }
        val updated = scoreboard.copy(status = "COMPLETED", updatedAt = System.currentTimeMillis())
        dao.putScoreboard(updated)
        syncScoreboardToRemote(updated)
        broadcastScoreboardOnLan(updated)
        dao.putScoreboardEvent(
            ScoreboardEventEntity(
                id = "EVT-${UUID.randomUUID().toString().take(8).uppercase()}",
                matchId = matchId,
                timestampText = SimpleDateFormat("HH:mm", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }.format(Date()),
                eventType = "MATCH_FINISH",
                description = "Match concluded. Final result: ${scoreboard.detailText}",
            ),
        )
    }

    suspend fun updateMatchScore(
        userId: String,
        matchId: String,
        scoreA: Int,
        scoreB: Int,
        detailText: String,
        eventDescription: String,
        userEmail: String = "",
    ) {
        val scoreboard = dao.getScoreboard(matchId) ?: error("Scoreboard not found")
        val booking = dao.getBooking(scoreboard.bookingId)
        val venue = dao.getVenue(scoreboard.venueId)
        val canControl = scoreboard.controllerUserId == userId ||
                (booking != null && (booking.playerId == userId || (userEmail.isNotBlank() && booking.playerEmail.equals(userEmail, ignoreCase = true)))) ||
                (venue != null && (venue.managerId == userId || (userEmail.isNotBlank() && venue.email.equals(userEmail, ignoreCase = true)))) ||
                userId.startsWith("mgr_")
        require(canControl) {
            "You do not have administrative permissions to update this match scoreboard."
        }

        val updated = scoreboard.copy(
            scoreA = scoreA,
            scoreB = scoreB,
            detailText = detailText.ifBlank { scoreboard.detailText },
            updatedAt = System.currentTimeMillis(),
        )
        dao.putScoreboard(updated)
        syncScoreboardToRemote(updated)
        broadcastScoreboardOnLan(updated)

        if (eventDescription.isNotBlank()) {
            dao.putScoreboardEvent(
                ScoreboardEventEntity(
                    id = "EVT-${UUID.randomUUID().toString().take(8).uppercase()}",
                    matchId = matchId,
                    timestampText = SimpleDateFormat("HH:mm", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }.format(Date()),
                    eventType = "SCORE_UPDATE",
                    description = eventDescription.trim(),
                ),
            )
        }
    }

    suspend fun updateMatchTeams(
        userId: String,
        matchId: String,
        teamA: String,
        teamB: String,
        userEmail: String = "",
    ) {
        val scoreboard = dao.getScoreboard(matchId) ?: error("Scoreboard not found")
        val booking = dao.getBooking(scoreboard.bookingId)
        val venue = dao.getVenue(scoreboard.venueId)
        val canControl = scoreboard.controllerUserId == userId ||
                (booking != null && (booking.playerId == userId || (userEmail.isNotBlank() && booking.playerEmail.equals(userEmail, ignoreCase = true)))) ||
                (venue != null && (venue.managerId == userId || (userEmail.isNotBlank() && venue.email.equals(userEmail, ignoreCase = true)))) ||
                userId.startsWith("mgr_")
        require(canControl) {
            "Only the player who booked this session or venue manager can edit squad names."
        }

        val cleanA = teamA.trim().ifBlank { scoreboard.teamA }
        val cleanB = teamB.trim().ifBlank { scoreboard.teamB }
        val updated = scoreboard.copy(
            teamA = cleanA,
            teamB = cleanB,
            updatedAt = System.currentTimeMillis(),
        )
        dao.putScoreboard(updated)
        syncScoreboardToRemote(updated)
        broadcastScoreboardOnLan(updated)

        dao.putScoreboardEvent(
            ScoreboardEventEntity(
                id = "EVT-${UUID.randomUUID().toString().take(8).uppercase()}",
                matchId = matchId,
                timestampText = SimpleDateFormat("HH:mm", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }.format(Date()),
                eventType = "TEAM_UPDATE",
                description = "Squads updated: $cleanA vs $cleanB",
            ),
        )
    }

    suspend fun updateUserProfile(
        userId: String,
        name: String,
        phone: String = "",
    ): UserEntity {
        val existing = dao.getUser(userId) ?: UserEntity(
            id = userId,
            googleAccountId = "user_$userId",
            name = name,
            email = "",
            role = "PLAYER",
        )
        val updated = existing.copy(
            name = name.trim().ifBlank { existing.name },
            phone = phone.trim().ifBlank { existing.phone },
            updatedAt = System.currentTimeMillis(),
        )
        dao.putUser(updated)
        runCatching {
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(userId).set(updated)
        }
        return updated
    }

    // Seeding Realistic Initial Data for All Venues & Sports
    suspend fun initializeSeedDataIfNeeded() {
        if (dao.getVenue("VEN-TURF-01") != null) return

        val managerId = "mgr_ps22_demo"
        val istTz = TimeZone.getTimeZone("Asia/Kolkata")
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).apply { timeZone = istTz }.format(Date())
        val tomorrowCal = Calendar.getInstance(istTz).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrowStr = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).apply { timeZone = istTz }.format(tomorrowCal.time)

        // 1. Venues
        val venue1 = VenueEntity(
            id = "VEN-TURF-01",
            managerId = managerId,
            name = "Premier Turf & Arena",
            venueType = "Sports Turf",
            supportedSports = "Football, Cricket",
            description = "High-grade FIFA certified artificial grass turf with LED floodlights, professional dugout, and digital scoreboard.",
            phone = "+91 98765 11223",
            email = "booking@premierturf.in",
            address = "Plot 14, Sports Complex Road",
            area = "Jubilee Hills",
            city = "Hyderabad",
            operatingHours = "Mon-Sun: 6:00 AM - 11:00 PM",
            facilities = "Night Lighting, Changing Room, Parking, Washroom, Drinking Water, First Aid",
            rules = "Flat turf shoes mandatory. No metal studs allowed.",
            cancellationPolicy = "Free cancellation up to 4 hours before kickoff.",
            coverImage = "venue_football",
            images = "venue_football",
            rating = 4.9f,
            reviewCount = 42,
        )

        val venue2 = VenueEntity(
            id = "VEN-BADM-02",
            managerId = managerId,
            name = "Metro Badminton Club",
            venueType = "Indoor Court",
            supportedSports = "Badminton",
            description = "BWF spec synthetic wooden badminton courts with anti-glare LED lighting, air conditioning, and locker rooms.",
            phone = "+91 98765 44556",
            email = "contact@metrobadminton.in",
            address = "Court #3, Metro Sports Hub",
            area = "Kukatpally",
            city = "Hyderabad",
            operatingHours = "Mon-Sun: 5:00 AM - 10:00 PM",
            facilities = "Air Conditioned, Lockers, Wi-Fi, Drinking Water, Showers",
            rules = "Non-marking gum sole shoes mandatory.",
            cancellationPolicy = "Full refund if cancelled 6 hours in advance.",
            coverImage = "venue_badminton",
            images = "venue_badminton",
            rating = 4.8f,
            reviewCount = 28,
        )

        val venue3 = VenueEntity(
            id = "VEN-BASK-03",
            managerId = managerId,
            name = "Sunrise Sports Court",
            venueType = "Indoor Arena",
            supportedSports = "Basketball, Volleyball",
            description = "FIBA standard wooden basketball court and tournament volleyball court with spectator seating and floodlights.",
            phone = "+91 98765 77889",
            email = "info@sunrisesports.in",
            address = "Sector 5, Outer Ring Road",
            area = "Jubilee Hills",
            city = "Hyderabad",
            operatingHours = "Mon-Sun: 6:00 AM - 10:00 PM",
            facilities = "Spectator Seating, Parking, Washroom, Drinking Water, First Aid",
            rules = "Standard basketball/court shoes mandatory.",
            cancellationPolicy = "Free cancellation up to 4 hours before session.",
            coverImage = "venue_basketball",
            images = "venue_basketball",
            rating = 4.7f,
            reviewCount = 19,
        )

        val venue4 = VenueEntity(
            id = "VEN-CRIC-04",
            managerId = managerId,
            name = "Gachibowli Sports Turf",
            venueType = "Cricket Ground",
            supportedSports = "Cricket",
            description = "Premier cricket ground featuring natural clay pitch, lush outfield, floodlights, dugouts, and electronic scoreboards.",
            phone = "+91 98765 99001",
            email = "cricket@gachibowlisports.in",
            address = "Stadium Road, Near Tech Park",
            area = "Gachibowli",
            city = "Hyderabad",
            operatingHours = "Mon-Sun: 6:00 AM - 11:00 PM",
            facilities = "Floodlights, Pavilion, Changing Rooms, Parking, Practice Nets",
            rules = "Spikes allowed on pitch area only. White/colored kits permitted.",
            cancellationPolicy = "Free cancellation up to 6 hours before match.",
            coverImage = "venue_cricket",
            images = "venue_cricket",
            rating = 4.9f,
            reviewCount = 56,
        )

        val venue5 = VenueEntity(
            id = "VEN-TENN-05",
            managerId = managerId,
            name = "Grand Slam Tennis Academy",
            venueType = "Tennis Court",
            supportedSports = "Tennis",
            description = "US Open style synthetic hard tennis courts with anti-glare floodlighting, ball machines, and spectator gallery.",
            phone = "+91 98765 33445",
            email = "tennis@grandslam.in",
            address = "Road #12, Banjara Hills",
            area = "Banjara Hills",
            city = "Hyderabad",
            operatingHours = "Mon-Sun: 6:00 AM - 10:00 PM",
            facilities = "Pro Shop, Lockers, Floodlights, Ball Machine, Refreshments",
            rules = "Non-marking tennis shoes mandatory. White or tennis attire required.",
            cancellationPolicy = "Free cancellation up to 4 hours before session.",
            coverImage = "court_tennis",
            images = "court_tennis",
            rating = 4.9f,
            reviewCount = 34,
        )

        val venue6 = VenueEntity(
            id = "VEN-TABLE-06",
            managerId = managerId,
            name = "Champions Table Tennis & Aquatic Hub",
            venueType = "Sports Complex",
            supportedSports = "Table Tennis, Volleyball",
            description = "ITTF approved Stiga table tennis tables with wooden flooring and tournament volleyball court with floodlighting.",
            phone = "+91 98765 66778",
            email = "info@championhub.in",
            address = "Phase 2, HITEC City",
            area = "HITEC City",
            city = "Hyderabad",
            operatingHours = "Mon-Sun: 5:30 AM - 9:30 PM",
            facilities = "Lockers, Showers, Air Conditioned TT Hall, Cafe, Parking",
            rules = "Non-marking indoor court shoes mandatory.",
            cancellationPolicy = "Free cancellation up to 4 hours before slot.",
            coverImage = "court_volleyball",
            images = "court_volleyball",
            rating = 4.8f,
            reviewCount = 41,
        )

        dao.putVenue(venue1)
        dao.putVenue(venue2)
        dao.putVenue(venue3)
        dao.putVenue(venue4)
        dao.putVenue(venue5)
        dao.putVenue(venue6)

        // 2. Equipment for All Venues & Sports
        val equipmentList = listOf(
            // Football Equipment (Venue 1)
            EquipmentEntity("EQP-01", venue1.id, "Football", "Match Football", 6, isIncludedFree = true, additionalCharge = 0.0, notes = "Size 5 official match ball"),
            EquipmentEntity("EQP-02", venue1.id, "Football", "Team Bibs Set", 14, isIncludedFree = true, additionalCharge = 0.0, notes = "Orange & Blue bibs"),
            EquipmentEntity("EQP-03", venue1.id, "Football", "Goalkeeper Gloves Pair", 4, isIncludedFree = false, additionalCharge = 50.0, notes = "All sizes available"),
            EquipmentEntity("EQP-04", venue1.id, "Football", "Training Cones Set", 20, isIncludedFree = true, additionalCharge = 0.0, notes = "For agility drills"),

            // Cricket Equipment (Venue 1 & 4)
            EquipmentEntity("EQP-05", venue1.id, "Cricket", "Kashmir Willow Bat", 4, isIncludedFree = false, additionalCharge = 150.0, notes = "Heavy & light bats"),
            EquipmentEntity("EQP-06", venue1.id, "Cricket", "Leather Match Ball", 6, isIncludedFree = false, additionalCharge = 100.0, notes = "Red leather balls"),
            EquipmentEntity("EQP-07", venue1.id, "Cricket", "Stumps & Bails Set", 2, isIncludedFree = true, additionalCharge = 0.0, notes = "Spring stumps"),

            // Badminton Equipment (Venue 2)
            EquipmentEntity("EQP-08", venue2.id, "Badminton", "Yonex Carbon Racket", 8, isIncludedFree = false, additionalCharge = 50.0, notes = "Strung with BG65"),
            EquipmentEntity("EQP-09", venue2.id, "Badminton", "Mavis 350 Shuttlecocks Box", 10, isIncludedFree = true, additionalCharge = 0.0, notes = "Yellow nylon shuttles"),
            EquipmentEntity("EQP-10", venue2.id, "Badminton", "Badminton Court Net", 4, isIncludedFree = true, additionalCharge = 0.0, notes = "BWF height set"),

            // Basketball & Volleyball Equipment (Venue 3)
            EquipmentEntity("EQP-11", venue3.id, "Basketball", "Wilson Official Basketball", 6, isIncludedFree = true, additionalCharge = 0.0, notes = "Size 7 official leather"),
            EquipmentEntity("EQP-12", venue3.id, "Volleyball", "Mikasa Volleyball", 6, isIncludedFree = true, additionalCharge = 0.0, notes = "Soft touch tournament ball"),
            EquipmentEntity("EQP-13", venue3.id, "Volleyball", "Volleyball Net & Antennas", 2, isIncludedFree = true, additionalCharge = 0.0, notes = "Pro net height"),

            // Tennis Equipment (Venue 5)
            EquipmentEntity("EQP-14", venue5.id, "Tennis", "Wilson Pro Rackets", 4, isIncludedFree = false, additionalCharge = 100.0, notes = "Pro graphite rackets"),
            EquipmentEntity("EQP-15", venue5.id, "Tennis", "Championship Tennis Balls Can", 6, isIncludedFree = true, additionalCharge = 0.0, notes = "Pressurized match balls"),

            // Table Tennis Equipment (Venue 6)
            EquipmentEntity("EQP-16", venue6.id, "Table Tennis", "Stiga TT Paddles Set", 8, isIncludedFree = true, additionalCharge = 0.0, notes = "Pro 3-star rubbers"),
            EquipmentEntity("EQP-17", venue6.id, "Table Tennis", "ITTF 40+ Seamless Balls Box", 12, isIncludedFree = true, additionalCharge = 0.0, notes = "3-star white balls"),
        )
        equipmentList.forEach { dao.putEquipment(it) }

        // 3. Pre-seed authentic active sessions in Indian Standard Time (IST)
        val seedSessions = listOf(
            SessionEntity(
                id = "SES-SEED-01",
                venueId = venue1.id,
                sport = "Football",
                date = todayStr,
                startTime = "05:00 PM",
                endTime = "06:00 PM",
                durationMinutes = 60,
                capacity = 22,
                price = 1200.0,
                status = "AVAILABLE",
            ),
            SessionEntity(
                id = "SES-SEED-02",
                venueId = venue1.id,
                sport = "Cricket",
                date = todayStr,
                startTime = "06:30 PM",
                endTime = "08:00 PM",
                durationMinutes = 90,
                capacity = 22,
                price = 1800.0,
                status = "AVAILABLE",
            ),
            SessionEntity(
                id = "SES-SEED-03",
                venueId = venue2.id,
                sport = "Badminton",
                date = todayStr,
                startTime = "08:00 PM",
                endTime = "09:00 PM",
                durationMinutes = 60,
                capacity = 4,
                price = 600.0,
                status = "AVAILABLE",
            ),
            SessionEntity(
                id = "SES-SEED-04",
                venueId = venue3.id,
                sport = "Basketball",
                date = tomorrowStr,
                startTime = "07:00 AM",
                endTime = "08:00 AM",
                durationMinutes = 60,
                capacity = 10,
                price = 800.0,
                status = "AVAILABLE",
            ),
        )
        dao.putSessions(seedSessions)
    }

    private fun startRemoteSync() {
        startFirestoreSync()
        startLanSync()
    }

    private fun startFirestoreSync() {
        runCatching {
            val firestore = FirebaseFirestore.getInstance()

            // 1. Listen for sessions from other devices in real-time
            sessionListener = firestore.collection("sessions")
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null) return@addSnapshotListener
                    repoScope.launch {
                        for (doc in snapshot.documents) {
                            runCatching {
                                val s = SessionEntity(
                                    id = doc.getString("id") ?: doc.id,
                                    venueId = doc.getString("venueId") ?: "",
                                    sport = doc.getString("sport") ?: "",
                                    date = doc.getString("date") ?: "",
                                    startTime = doc.getString("startTime") ?: "",
                                    endTime = doc.getString("endTime") ?: "",
                                    durationMinutes = (doc.getLong("durationMinutes") ?: 60L).toInt(),
                                    capacity = (doc.getLong("capacity") ?: 10L).toInt(),
                                    bookedCount = (doc.getLong("bookedCount") ?: 0L).toInt(),
                                    price = doc.getDouble("price") ?: 0.0,
                                    status = doc.getString("status") ?: "AVAILABLE",
                                    coverImage = doc.getString("coverImage") ?: "",
                                )
                                if (s.venueId.isNotBlank() && s.sport.isNotBlank()) {
                                    dao.putSession(s)
                                }
                            }
                        }
                    }
                }

            // 2. Listen for venues
            venueListener = firestore.collection("venues")
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null) return@addSnapshotListener
                    repoScope.launch {
                        for (doc in snapshot.documents) {
                            runCatching {
                                val v = VenueEntity(
                                    id = doc.getString("id") ?: doc.id,
                                    managerId = doc.getString("managerId") ?: "",
                                    name = doc.getString("name") ?: "",
                                    venueType = doc.getString("venueType") ?: "Arena",
                                    supportedSports = doc.getString("supportedSports") ?: "Football",
                                    description = doc.getString("description") ?: "",
                                    phone = doc.getString("phone") ?: "",
                                    email = doc.getString("email") ?: "",
                                    address = doc.getString("address") ?: "",
                                    area = doc.getString("area") ?: "",
                                    city = doc.getString("city") ?: "",
                                    operatingHours = doc.getString("operatingHours") ?: "",
                                    facilities = doc.getString("facilities") ?: "",
                                    rules = doc.getString("rules") ?: "",
                                    cancellationPolicy = doc.getString("cancellationPolicy") ?: "",
                                    coverImage = doc.getString("coverImage") ?: "",
                                    images = doc.getString("images") ?: "",
                                    rating = (doc.getDouble("rating") ?: 4.8).toFloat(),
                                    reviewCount = (doc.getLong("reviewCount") ?: 0L).toInt(),
                                )
                                if (v.name.isNotBlank()) {
                                    dao.putVenue(v)
                                }
                            }
                        }
                    }
                }

            // 3. Listen for bookings
            bookingListener = firestore.collection("bookings")
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null) return@addSnapshotListener
                    repoScope.launch {
                        for (doc in snapshot.documents) {
                            runCatching {
                                val b = BookingEntity(
                                    id = doc.getString("id") ?: doc.id,
                                    bookingReference = doc.getString("bookingReference") ?: "",
                                    playerId = doc.getString("playerId") ?: "",
                                    playerName = doc.getString("playerName") ?: "",
                                    playerPhone = doc.getString("playerPhone") ?: "",
                                    playerEmail = doc.getString("playerEmail") ?: "",
                                    playerCount = (doc.getLong("playerCount") ?: 2L).toInt(),
                                    venueId = doc.getString("venueId") ?: "",
                                    venueName = doc.getString("venueName") ?: "",
                                    sessionId = doc.getString("sessionId") ?: "",
                                    sport = doc.getString("sport") ?: "",
                                    date = doc.getString("date") ?: "",
                                    startTime = doc.getString("startTime") ?: "",
                                    endTime = doc.getString("endTime") ?: "",
                                    price = doc.getDouble("price") ?: 800.0,
                                    equipmentRequested = doc.getString("equipmentRequested") ?: "",
                                    notes = doc.getString("notes") ?: "",
                                    status = doc.getString("status") ?: "PENDING",
                                    rejectionReason = doc.getString("rejectionReason") ?: "",
                                    cancellationReason = doc.getString("cancellationReason") ?: "",
                                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                                )
                                val local = dao.getBooking(b.id)
                                if (local == null || b.updatedAt >= local.updatedAt) {
                                    dao.putBooking(b)
                                }
                            }
                        }
                    }
                }

            // 4. Listen for notifications
            notificationListener = firestore.collection("notifications")
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null) return@addSnapshotListener
                    repoScope.launch {
                        for (doc in snapshot.documents) {
                            runCatching {
                                val n = NotificationEntity(
                                    id = doc.getString("id") ?: doc.id,
                                    userId = doc.getString("userId") ?: "",
                                    title = doc.getString("title") ?: "",
                                    message = doc.getString("message") ?: "",
                                    type = doc.getString("type") ?: "SYSTEM",
                                    targetEntityId = doc.getString("targetEntityId") ?: "",
                                    isRead = doc.getBoolean("isRead") ?: false,
                                    timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                                )
                                dao.putNotification(n)
                                if (!n.isRead && isForActiveUser(n.userId)) {
                                    SystemNotificationHelper.showNotification(
                                        appContext,
                                        n.title,
                                        n.message,
                                        n.id.hashCode(),
                                    )
                                }
                            }
                        }
                    }
                }

            // 5. Listen for match scoreboards
            firestore.collection("match_scoreboards")
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null) return@addSnapshotListener
                    repoScope.launch {
                        for (doc in snapshot.documents) {
                            runCatching {
                                val m = MatchScoreboardEntity(
                                    id = doc.getString("id") ?: doc.id,
                                    bookingId = doc.getString("bookingId") ?: "",
                                    venueId = doc.getString("venueId") ?: "",
                                    sport = doc.getString("sport") ?: "",
                                    teamA = doc.getString("teamA") ?: "",
                                    teamB = doc.getString("teamB") ?: "",
                                    scoreA = (doc.getLong("scoreA") ?: 0L).toInt(),
                                    scoreB = (doc.getLong("scoreB") ?: 0L).toInt(),
                                    detailText = doc.getString("detailText") ?: "",
                                    status = doc.getString("status") ?: "SCHEDULED",
                                    controllerUserId = doc.getString("controllerUserId") ?: "",
                                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                                )
                                val local = dao.getScoreboard(m.id)
                                if (local == null || m.updatedAt >= local.updatedAt) {
                                    dao.putScoreboard(m)
                                }
                            }
                        }
                    }
                }
        }
    }

    private fun syncSessionToRemote(session: SessionEntity) {
        broadcastSessionOnLan(session)
        runCatching {
            val map = hashMapOf(
                "id" to session.id,
                "venueId" to session.venueId,
                "sport" to session.sport,
                "date" to session.date,
                "startTime" to session.startTime,
                "endTime" to session.endTime,
                "durationMinutes" to session.durationMinutes,
                "capacity" to session.capacity,
                "bookedCount" to session.bookedCount,
                "price" to session.price,
                "status" to session.status,
                "coverImage" to session.coverImage,
                "syncedAt" to System.currentTimeMillis(),
            )
            FirebaseFirestore.getInstance().collection("sessions").document(session.id).set(map)
        }
    }

    private fun deleteSessionFromRemote(sessionId: String) {
        broadcastDeleteOnLan(sessionId)
        runCatching {
            FirebaseFirestore.getInstance().collection("sessions").document(sessionId).delete()
        }
    }

    private fun syncVenueToRemote(venue: VenueEntity) {
        runCatching {
            val map = hashMapOf(
                "id" to venue.id,
                "managerId" to venue.managerId,
                "name" to venue.name,
                "venueType" to venue.venueType,
                "supportedSports" to venue.supportedSports,
                "description" to venue.description,
                "phone" to venue.phone,
                "email" to venue.email,
                "address" to venue.address,
                "area" to venue.area,
                "city" to venue.city,
                "operatingHours" to venue.operatingHours,
                "facilities" to venue.facilities,
                "rules" to venue.rules,
                "cancellationPolicy" to venue.cancellationPolicy,
                "coverImage" to venue.coverImage,
                "images" to venue.images,
                "rating" to venue.rating,
                "reviewCount" to venue.reviewCount,
                "syncedAt" to System.currentTimeMillis(),
            )
            FirebaseFirestore.getInstance().collection("venues").document(venue.id).set(map)
        }
    }

    private fun deleteVenueFromRemote(venueId: String) {
        runCatching {
            FirebaseFirestore.getInstance().collection("venues").document(venueId).delete()
        }
    }

    private fun syncBookingToRemote(booking: BookingEntity) {
        runCatching {
            val map = hashMapOf(
                "id" to booking.id,
                "bookingReference" to booking.bookingReference,
                "playerId" to booking.playerId,
                "playerName" to booking.playerName,
                "playerPhone" to booking.playerPhone,
                "playerEmail" to booking.playerEmail,
                "playerCount" to booking.playerCount,
                "venueId" to booking.venueId,
                "venueName" to booking.venueName,
                "sessionId" to booking.sessionId,
                "sport" to booking.sport,
                "date" to booking.date,
                "startTime" to booking.startTime,
                "endTime" to booking.endTime,
                "price" to booking.price,
                "equipmentRequested" to booking.equipmentRequested,
                "notes" to booking.notes,
                "status" to booking.status,
                "rejectionReason" to booking.rejectionReason,
                "cancellationReason" to booking.cancellationReason,
                "createdAt" to booking.createdAt,
                "updatedAt" to booking.updatedAt,
                "syncedAt" to System.currentTimeMillis(),
            )
            FirebaseFirestore.getInstance().collection("bookings").document(booking.id).set(map)
        }
    }

    private fun syncNotificationToRemote(notification: NotificationEntity) {
        runCatching {
            val map = hashMapOf(
                "id" to notification.id,
                "userId" to notification.userId,
                "title" to notification.title,
                "message" to notification.message,
                "type" to notification.type,
                "targetEntityId" to notification.targetEntityId,
                "isRead" to notification.isRead,
                "timestamp" to notification.timestamp,
                "syncedAt" to System.currentTimeMillis(),
            )
            FirebaseFirestore.getInstance().collection("notifications").document(notification.id).set(map)
        }
    }

    private fun syncScoreboardToRemote(scoreboard: MatchScoreboardEntity) {
        runCatching {
            val map = hashMapOf(
                "id" to scoreboard.id,
                "bookingId" to scoreboard.bookingId,
                "venueId" to scoreboard.venueId,
                "sport" to scoreboard.sport,
                "teamA" to scoreboard.teamA,
                "teamB" to scoreboard.teamB,
                "scoreA" to scoreboard.scoreA,
                "scoreB" to scoreboard.scoreB,
                "detailText" to scoreboard.detailText,
                "status" to scoreboard.status,
                "controllerUserId" to scoreboard.controllerUserId,
                "updatedAt" to scoreboard.updatedAt,
                "syncedAt" to System.currentTimeMillis(),
            )
            FirebaseFirestore.getInstance().collection("match_scoreboards").document(scoreboard.id).set(map)
        }
    }

    // LAN Peer-to-Peer Synchronization via UDP Broadcast
    private fun broadcastVenueOnLan(venue: VenueEntity) {
        repoScope.launch(Dispatchers.IO) {
            runCatching {
                val json = JSONObject().apply {
                    put("type", "VENUE_SYNC")
                    put("id", venue.id)
                    put("managerId", venue.managerId)
                    put("name", venue.name)
                    put("venueType", venue.venueType)
                    put("supportedSports", venue.supportedSports)
                    put("description", venue.description)
                    put("phone", venue.phone)
                    put("email", venue.email)
                    put("address", venue.address)
                    put("area", venue.area)
                    put("city", venue.city)
                    put("operatingHours", venue.operatingHours)
                    put("facilities", venue.facilities)
                    put("rules", venue.rules)
                    put("cancellationPolicy", venue.cancellationPolicy)
                    put("coverImage", venue.coverImage)
                    put("images", venue.images)
                    put("rating", venue.rating.toDouble())
                    put("reviewCount", venue.reviewCount)
                }
                sendUdpBroadcast(json.toString())
            }
        }
    }

    private fun broadcastVenueDeleteOnLan(venueId: String) {
        repoScope.launch(Dispatchers.IO) {
            runCatching {
                val json = JSONObject().apply {
                    put("type", "VENUE_DELETE")
                    put("venueId", venueId)
                }
                sendUdpBroadcast(json.toString())
            }
        }
    }

    private fun broadcastSessionOnLan(session: SessionEntity) {
        repoScope.launch(Dispatchers.IO) {
            runCatching {
                val json = JSONObject().apply {
                    put("type", "SESSION_SYNC")
                    put("id", session.id)
                    put("venueId", session.venueId)
                    put("sport", session.sport)
                    put("date", session.date)
                    put("startTime", session.startTime)
                    put("endTime", session.endTime)
                    put("durationMinutes", session.durationMinutes)
                    put("capacity", session.capacity)
                    put("bookedCount", session.bookedCount)
                    put("price", session.price)
                    put("status", session.status)
                    put("coverImage", session.coverImage)
                }
                sendUdpBroadcast(json.toString())
            }
        }
    }

    private fun broadcastDeleteOnLan(sessionId: String) {
        repoScope.launch(Dispatchers.IO) {
            runCatching {
                val json = JSONObject().apply {
                    put("type", "SESSION_DELETE")
                    put("sessionId", sessionId)
                }
                sendUdpBroadcast(json.toString())
            }
        }
    }

    private fun broadcastBookingOnLan(booking: BookingEntity) {
        repoScope.launch(Dispatchers.IO) {
            runCatching {
                val json = JSONObject().apply {
                    put("type", "BOOKING_SYNC")
                    put("id", booking.id)
                    put("bookingReference", booking.bookingReference)
                    put("playerId", booking.playerId)
                    put("playerName", booking.playerName)
                    put("playerPhone", booking.playerPhone)
                    put("playerEmail", booking.playerEmail)
                    put("playerCount", booking.playerCount)
                    put("venueId", booking.venueId)
                    put("venueName", booking.venueName)
                    put("sessionId", booking.sessionId)
                    put("sport", booking.sport)
                    put("date", booking.date)
                    put("startTime", booking.startTime)
                    put("endTime", booking.endTime)
                    put("price", booking.price)
                    put("equipmentRequested", booking.equipmentRequested)
                    put("notes", booking.notes)
                    put("status", booking.status)
                    put("rejectionReason", booking.rejectionReason)
                    put("cancellationReason", booking.cancellationReason)
                    put("createdAt", booking.createdAt)
                    put("updatedAt", booking.updatedAt)
                }
                sendUdpBroadcast(json.toString())
            }
        }
    }

    private fun broadcastNotificationOnLan(notification: NotificationEntity) {
        repoScope.launch(Dispatchers.IO) {
            runCatching {
                val json = JSONObject().apply {
                    put("type", "NOTIFICATION_SYNC")
                    put("id", notification.id)
                    put("userId", notification.userId)
                    put("title", notification.title)
                    put("message", notification.message)
                    put("notifType", notification.type)
                    put("targetEntityId", notification.targetEntityId)
                    put("isRead", notification.isRead)
                    put("timestamp", notification.timestamp)
                }
                sendUdpBroadcast(json.toString())
            }
        }
    }

    private fun broadcastScoreboardOnLan(scoreboard: MatchScoreboardEntity) {
        repoScope.launch(Dispatchers.IO) {
            runCatching {
                val json = JSONObject().apply {
                    put("type", "SCOREBOARD_SYNC")
                    put("id", scoreboard.id)
                    put("bookingId", scoreboard.bookingId)
                    put("venueId", scoreboard.venueId)
                    put("sport", scoreboard.sport)
                    put("teamA", scoreboard.teamA)
                    put("teamB", scoreboard.teamB)
                    put("scoreA", scoreboard.scoreA)
                    put("scoreB", scoreboard.scoreB)
                    put("detailText", scoreboard.detailText)
                    put("status", scoreboard.status)
                    put("controllerUserId", scoreboard.controllerUserId)
                    put("updatedAt", scoreboard.updatedAt)
                }
                sendUdpBroadcast(json.toString())
            }
        }
    }

    private fun sendUdpBroadcast(dataStr: String) {
        runCatching {
            val socket = DatagramSocket()
            socket.broadcast = true
            val bytes = dataStr.toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(bytes, bytes.size, InetAddress.getByName("255.255.255.255"), 9876)
            socket.send(packet)
            socket.close()
        }
    }

    private fun startLanSync() {
        if (lanSyncStarted) return
        lanSyncStarted = true
        repoScope.launch(Dispatchers.IO) {
            runCatching {
                val socket = DatagramSocket(9876)
                socket.broadcast = true
                val buffer = ByteArray(8192)
                while (true) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val jsonStr = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    runCatching {
                        val obj = JSONObject(jsonStr)
                        when (obj.optString("type")) {
                            "VENUE_SYNC" -> {
                                val v = VenueEntity(
                                    id = obj.getString("id"),
                                    managerId = obj.optString("managerId", ""),
                                    name = obj.getString("name"),
                                    venueType = obj.optString("venueType", "Arena"),
                                    supportedSports = obj.optString("supportedSports", "Football"),
                                    description = obj.optString("description", ""),
                                    phone = obj.optString("phone", ""),
                                    email = obj.optString("email", ""),
                                    address = obj.optString("address", ""),
                                    area = obj.optString("area", ""),
                                    city = obj.optString("city", ""),
                                    operatingHours = obj.optString("operatingHours", ""),
                                    facilities = obj.optString("facilities", ""),
                                    rules = obj.optString("rules", ""),
                                    cancellationPolicy = obj.optString("cancellationPolicy", ""),
                                    coverImage = obj.optString("coverImage", ""),
                                    images = obj.optString("images", ""),
                                    rating = obj.optDouble("rating", 4.8).toFloat(),
                                    reviewCount = obj.optInt("reviewCount", 0),
                                )
                                if (v.name.isNotBlank()) {
                                    dao.putVenue(v)
                                }
                            }
                            "VENUE_DELETE" -> {
                                val venueId = obj.getString("venueId")
                                dao.deleteVenue(venueId)
                            }
                            "SESSION_SYNC" -> {
                                val s = SessionEntity(
                                    id = obj.getString("id"),
                                    venueId = obj.getString("venueId"),
                                    sport = obj.getString("sport"),
                                    date = obj.getString("date"),
                                    startTime = obj.getString("startTime"),
                                    endTime = obj.getString("endTime"),
                                    durationMinutes = obj.getInt("durationMinutes"),
                                    capacity = obj.getInt("capacity"),
                                    bookedCount = obj.optInt("bookedCount", 0),
                                    price = obj.getDouble("price"),
                                    status = obj.optString("status", "AVAILABLE"),
                                    coverImage = obj.optString("coverImage", ""),
                                )
                                dao.putSession(s)
                            }
                            "SESSION_DELETE" -> {
                                val sessionId = obj.getString("sessionId")
                                dao.deleteSession(sessionId)
                            }
                            "BOOKING_SYNC" -> {
                                val b = BookingEntity(
                                    id = obj.getString("id"),
                                    bookingReference = obj.optString("bookingReference", ""),
                                    playerId = obj.optString("playerId", ""),
                                    playerName = obj.optString("playerName", ""),
                                    playerPhone = obj.optString("playerPhone", ""),
                                    playerEmail = obj.optString("playerEmail", ""),
                                    playerCount = obj.optInt("playerCount", 2),
                                    venueId = obj.optString("venueId", ""),
                                    venueName = obj.optString("venueName", ""),
                                    sessionId = obj.optString("sessionId", ""),
                                    sport = obj.optString("sport", ""),
                                    date = obj.optString("date", ""),
                                    startTime = obj.optString("startTime", ""),
                                    endTime = obj.optString("endTime", ""),
                                    price = obj.optDouble("price", 800.0),
                                    equipmentRequested = obj.optString("equipmentRequested", ""),
                                    notes = obj.optString("notes", ""),
                                    status = obj.optString("status", "PENDING"),
                                    rejectionReason = obj.optString("rejectionReason", ""),
                                    cancellationReason = obj.optString("cancellationReason", ""),
                                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                                )
                                val local = dao.getBooking(b.id)
                                if (local == null || b.updatedAt >= local.updatedAt) {
                                    dao.putBooking(b)
                                }
                            }
                            "SCOREBOARD_SYNC" -> {
                                val m = MatchScoreboardEntity(
                                    id = obj.getString("id"),
                                    bookingId = obj.optString("bookingId", ""),
                                    venueId = obj.optString("venueId", ""),
                                    sport = obj.optString("sport", ""),
                                    teamA = obj.optString("teamA", ""),
                                    teamB = obj.optString("teamB", ""),
                                    scoreA = obj.optInt("scoreA", 0),
                                    scoreB = obj.optInt("scoreB", 0),
                                    detailText = obj.optString("detailText", ""),
                                    status = obj.optString("status", "SCHEDULED"),
                                    controllerUserId = obj.optString("controllerUserId", ""),
                                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                                )
                                val local = dao.getScoreboard(m.id)
                                if (local == null || m.updatedAt >= local.updatedAt) {
                                    dao.putScoreboard(m)
                                }
                            }
                            "NOTIFICATION_SYNC" -> {
                                val n = NotificationEntity(
                                    id = obj.getString("id"),
                                    userId = obj.optString("userId", ""),
                                    title = obj.optString("title", ""),
                                    message = obj.optString("message", ""),
                                    type = obj.optString("notifType", "SYSTEM"),
                                    targetEntityId = obj.optString("targetEntityId", ""),
                                    isRead = obj.optBoolean("isRead", false),
                                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                                )
                                dao.putNotification(n)
                                if (!n.isRead && isForActiveUser(n.userId)) {
                                    SystemNotificationHelper.showNotification(
                                        appContext,
                                        n.title,
                                        n.message,
                                        n.id.hashCode(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
