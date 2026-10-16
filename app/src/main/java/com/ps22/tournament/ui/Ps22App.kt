package com.ps22.tournament.ui

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import java.util.UUID
import com.ps22.tournament.BuildConfig
import com.ps22.tournament.R
import com.ps22.tournament.data.*
import com.ps22.tournament.notifications.SystemNotificationHelper
import com.ps22.tournament.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private enum class PlayerTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    EXPLORE("Explore", Icons.Rounded.Explore),
    BOOKINGS("Bookings", Icons.Rounded.ConfirmationNumber),
    SCOREBOARD("Live Score", Icons.Rounded.SportsScore),
    NOTIFICATIONS("Notifications", Icons.Rounded.Notifications),
    PROFILE("Profile", Icons.Rounded.Person),
}

private enum class ManagerTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DASHBOARD("Dashboard", Icons.Rounded.Dashboard),
    VENUES("Venues", Icons.Rounded.Store),
    SCHEDULE("Schedule", Icons.Rounded.CalendarMonth),
    REQUESTS("Requests", Icons.Rounded.MoveToInbox),
    NOTIFICATIONS("Notifications", Icons.Rounded.Notifications),
    PROFILE("Settings", Icons.Rounded.Settings),
}

@Composable
fun Ps22App(
    repository: TournamentRepository,
    venueRepo: VenueBookingRepository,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }

    var currentUserId by remember { mutableStateOf("") }
    var userProfile by remember { mutableStateOf<UserEntity?>(null) }
    var selectedRole by remember { mutableStateOf<String?>(null) }
    var authStep by remember { mutableStateOf("AUTH") } // AUTH, ROLE_SELECT, ONBOARDING, MAIN
    var activePlayerTab by remember { mutableStateOf(PlayerTab.HOME) }
    var activeManagerTab by remember { mutableStateOf(ManagerTab.DASHBOARD) }

    var activeVenueId by remember { mutableStateOf<String?>(null) }
    var activeMatchId by remember { mutableStateOf<String?>(null) }
    var preselectedVenueId by remember { mutableStateOf<String?>(null) }
    var activeDialog by remember { mutableStateOf<String?>(null) }
    var snackMessage by remember { mutableStateOf<String?>(null) }
    var inspectedSession by remember { mutableStateOf<SessionEntity?>(null) }
    var editingSession by remember { mutableStateOf<SessionEntity?>(null) }
    var editingVenue by remember { mutableStateOf<VenueEntity?>(null) }
    var showEditProfileDialog by remember { mutableStateOf(false) }

    val performSignOut: () -> Unit = {
        scope.launch {
            runCatching {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().build()
                val gsc = GoogleSignIn.getClient(context, gso)
                gsc.signOut()
                gsc.revokeAccess()
            }
            runCatching { FirebaseAuth.getInstance().signOut() }
        }
        currentUserId = ""
        userProfile = null
        selectedRole = null
        authStep = "AUTH"
        activeVenueId = null
        activeMatchId = null
    }

    // Seed initial data and inspect authenticated session
    LaunchedEffect(Unit) {
        runCatching {
            repository.initializeDemoIfNeeded()
            venueRepo.initializeSeedDataIfNeeded()
            venueRepo.cleanupExpiredSessions()
            val fbUser = FirebaseAuth.getInstance().currentUser
            if (fbUser != null && !fbUser.email.isNullOrBlank()) {
                val uid = "goog_${fbUser.email.hashCode()}"
                val existing = venueRepo.getUser(uid)
                if (existing != null && existing.role.isNotBlank()) {
                    currentUserId = existing.id
                    userProfile = existing
                    selectedRole = existing.role
                    authStep = "MAIN"
                }
            }
        }.onFailure { snackMessage = it.message }
    }

    LaunchedEffect(snackMessage) {
        snackMessage?.let { snack.showSnackbar(it); snackMessage = null }
    }

    LaunchedEffect(currentUserId, userProfile?.email) {
        if (currentUserId.isNotBlank()) {
            venueRepo.setActiveUser(currentUserId, userProfile?.email ?: "")
        }
    }

    val currentContext = LocalContext.current
    val liveNotifications by venueRepo.observeNotifications(currentUserId, userProfile?.email ?: "")
        .collectAsStateWithLifecycle(emptyList())
    val notifiedNotificationIds = remember { mutableSetOf<String>() }

    LaunchedEffect(liveNotifications) {
        if (currentUserId.isNotBlank()) {
            liveNotifications.filter { !it.isRead && it.id !in notifiedNotificationIds }.forEach { n ->
                notifiedNotificationIds.add(n.id)
                SystemNotificationHelper.showNotification(
                    currentContext,
                    n.title,
                    n.message,
                    n.id.hashCode(),
                )
            }
        }
    }

    val compact = with(androidx.compose.ui.platform.LocalDensity.current) {
        androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toDp() < 600.dp
    }

    if (authStep == "AUTH") {
        GoogleAuthScreen(
            venueRepo = venueRepo,
            onContinue = { email, name ->
                scope.launch {
                    val uid = "goog_${email.hashCode()}"
                    val user = venueRepo.getUser(uid)
                    if (user != null && user.role.isNotBlank()) {
                        currentUserId = user.id
                        userProfile = user
                        selectedRole = user.role
                        authStep = "MAIN"
                    } else {
                        currentUserId = uid
                        userProfile = UserEntity(
                            id = currentUserId,
                            googleAccountId = "google_${email.hashCode()}",
                            name = name,
                            email = email,
                            role = "",
                        )
                        authStep = "ROLE_SELECT"
                    }
                }
            },
        )
        return
    }

    if (authStep == "ROLE_SELECT") {
        RoleSelectionScreen(
            onRoleChosen = { role ->
                scope.launch {
                    selectedRole = role
                    val saved = venueRepo.saveUserRole(
                        userId = currentUserId,
                        googleAccountId = userProfile?.googleAccountId ?: "google_acc",
                        name = userProfile?.name ?: "User",
                        email = userProfile?.email ?: "",
                        role = role,
                    )
                    userProfile = saved
                    authStep = "ONBOARDING"
                }
            },
        )
        return
    }

    if (authStep == "ONBOARDING") {
        OnboardingScreen(
            role = selectedRole ?: "PLAYER",
            userName = userProfile?.name ?: "User",
            userEmail = userProfile?.email ?: "",
            onComplete = {
                authStep = "MAIN"
                snackMessage = "Welcome! Your ${if (selectedRole == "VENUE_MANAGER") "Venue Manager" else "Player"} workspace is ready."
            },
            venueRepo = venueRepo,
            currentUserId = currentUserId,
        )
        return
    }

    val role = selectedRole ?: "PLAYER"

    if (showEditProfileDialog) {
        var editName by remember(userProfile?.name) { mutableStateOf(userProfile?.name.orEmpty()) }
        var editPhone by remember(userProfile?.phone) { mutableStateOf(userProfile?.phone.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit Profile & Display Name", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Update your display name and contact details across PlayZone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Display Name / Squad Leader") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("Phone Number / Squad Tag") },
                        placeholder = { Text("e.g. +91 98765 43210") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val updated = venueRepo.updateUserProfile(currentUserId, editName, editPhone)
                            userProfile = updated
                            snackMessage = "Profile updated to ${updated.name}"
                            showEditProfileDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PlayZonePrimaryCoral),
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (compact) {
                NavigationBar(
                    containerColor = PlayZoneDeepNavy,
                    contentColor = Color.White,
                ) {
                    if (role == "PLAYER") {
                        PlayerTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = activePlayerTab == tab,
                                onClick = { activePlayerTab = tab; activeVenueId = null; activeMatchId = null },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PlayZonePrimaryCoral,
                                    selectedTextColor = PlayZonePrimaryCoral,
                                    unselectedIconColor = Color.LightGray,
                                    unselectedTextColor = Color.LightGray,
                                    indicatorColor = PlayZoneSecondaryNavy,
                                ),
                            )
                        }
                    } else {
                        ManagerTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = activeManagerTab == tab,
                                onClick = { activeManagerTab = tab; activeVenueId = null; activeMatchId = null },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PlayZonePrimaryCoral,
                                    selectedTextColor = PlayZonePrimaryCoral,
                                    unselectedIconColor = Color.LightGray,
                                    unselectedTextColor = Color.LightGray,
                                    indicatorColor = PlayZoneSecondaryNavy,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { insets ->
        Row(Modifier.fillMaxSize().padding(insets)) {
            if (!compact) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    if (role == "PLAYER") {
                        PlayerTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = activePlayerTab == tab,
                                onClick = { activePlayerTab = tab; activeVenueId = null; activeMatchId = null },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label) },
                            )
                        }
                    } else {
                        ManagerTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = activeManagerTab == tab,
                                onClick = { activeManagerTab = tab; activeVenueId = null; activeMatchId = null },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            }

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val maxContent = if (maxWidth > 900.dp) 960.dp else Dp.Infinity
                Column(Modifier.fillMaxSize().widthIn(max = maxContent).align(Alignment.TopCenter)) {
                    AppHeader(
                        user = userProfile,
                        role = role,
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = onToggleTheme,
                        onSignOut = performSignOut,
                        onEditProfile = { showEditProfileDialog = true },
                    )

                    if (activeVenueId != null) {
                        VenueDetailScreen(
                            venueId = activeVenueId!!,
                            venueRepo = venueRepo,
                            currentUserId = currentUserId,
                            userName = userProfile?.name ?: "",
                            userEmail = userProfile?.email ?: "",
                            userPhone = userProfile?.phone ?: "",
                            onBack = { activeVenueId = null },
                            onBookingSubmitted = { bookingRef ->
                                activeVenueId = null
                                activePlayerTab = PlayerTab.BOOKINGS
                                snackMessage = "Booking request submitted! Reference: $bookingRef"
                            },
                        )
                    } else if (activeMatchId != null) {
                        LiveScoreboardScreen(
                            matchId = activeMatchId!!,
                            venueRepo = venueRepo,
                            currentUserId = currentUserId,
                            currentUserEmail = userProfile?.email ?: "",
                            onBack = { activeMatchId = null },
                            onScoreUpdated = { snackMessage = "Scoreboard updated" },
                        )
                    } else if (role == "PLAYER") {
                        when (activePlayerTab) {
                            PlayerTab.HOME -> PlayerHomeScreen(
                                venueRepo = venueRepo,
                                userName = userProfile?.name ?: "Player",
                                onSelectVenue = { activeVenueId = it },
                                onOpenBookings = { activePlayerTab = PlayerTab.BOOKINGS },
                                onOpenScoreboard = { matchId -> activeMatchId = matchId },
                            )
                            PlayerTab.EXPLORE -> ExploreVenuesScreen(
                                venueRepo = venueRepo,
                                onSelectVenue = { activeVenueId = it },
                            )
                            PlayerTab.BOOKINGS -> PlayerBookingsScreen(
                                venueRepo = venueRepo,
                                playerId = currentUserId,
                                playerEmail = userProfile?.email ?: "",
                                onOpenScoreboard = { matchId -> activeMatchId = matchId },
                                onCancelBooking = { bookingId ->
                                    scope.launch {
                                        runCatching { venueRepo.cancelBooking(currentUserId, bookingId, "Cancelled by player") }
                                            .onSuccess { snackMessage = "Booking cancelled" }
                                            .onFailure { snackMessage = it.message }
                                    }
                                },
                                onExploreVenues = { activePlayerTab = PlayerTab.EXPLORE },
                            )
                            PlayerTab.SCOREBOARD -> LiveMatchesListScreen(
                                venueRepo = venueRepo,
                                onSelectMatch = { activeMatchId = it },
                            )
                            PlayerTab.NOTIFICATIONS -> NotificationsScreen(
                                venueRepo = venueRepo,
                                userId = currentUserId,
                                userEmail = userProfile?.email ?: "",
                            )
                            PlayerTab.PROFILE -> ProfileScreen(
                                user = userProfile,
                                role = role,
                                isDarkTheme = isDarkTheme,
                                onToggleTheme = onToggleTheme,
                                onSignOut = performSignOut,
                                onEditProfile = { showEditProfileDialog = true },
                            )
                        }
                    } else {
                        when (activeManagerTab) {
                            ManagerTab.DASHBOARD -> ManagerDashboardScreen(
                                venueRepo = venueRepo,
                                managerId = currentUserId,
                                managerEmail = userProfile?.email ?: "",
                                onOpenRequests = { activeManagerTab = ManagerTab.REQUESTS },
                                onAddSession = {
                                    preselectedVenueId = null
                                    activeDialog = "add_session"
                                },
                                onAddVenue = { activeDialog = "add_venue" },
                                onAddEquipment = {
                                    preselectedVenueId = null
                                    activeDialog = "add_equipment"
                                },
                                onSelectSession = { inspectedSession = it },
                                onEditSession = { editingSession = it },
                                onEditVenue = { editingVenue = it },
                                onDeleteVenue = { venueId ->
                                    scope.launch {
                                        runCatching { venueRepo.deleteVenue(venueId, currentUserId) }
                                            .onSuccess { snackMessage = "Venue deleted" }
                                    }
                                },
                                onDeleteSession = { sessionId ->
                                    scope.launch {
                                        runCatching { venueRepo.deleteSession(sessionId, currentUserId) }
                                            .onSuccess { snackMessage = "Session slot deleted" }
                                    }
                                },
                            )
                            ManagerTab.VENUES -> ManagerVenuesScreen(
                                venueRepo = venueRepo,
                                managerId = currentUserId,
                                managerEmail = userProfile?.email ?: "",
                                onAddVenue = { activeDialog = "add_venue" },
                                onAddSessionForVenue = { vId ->
                                    preselectedVenueId = vId
                                    activeDialog = "add_session"
                                },
                                onAddEquipmentForVenue = { vId ->
                                    preselectedVenueId = vId
                                    activeDialog = "add_equipment"
                                },
                                onEditVenue = { editingVenue = it },
                                onDeleteVenue = { venueId ->
                                    scope.launch {
                                        runCatching { venueRepo.deleteVenue(venueId, currentUserId) }
                                            .onSuccess { snackMessage = "Venue and its sessions removed" }
                                    }
                                },
                                onSelectSession = { inspectedSession = it },
                                onEditSession = { editingSession = it },
                                onDeleteSession = { sessionId ->
                                    scope.launch {
                                        runCatching { venueRepo.deleteSession(sessionId, currentUserId) }
                                            .onSuccess { snackMessage = "Session slot removed" }
                                    }
                                },
                            )
                            ManagerTab.SCHEDULE -> ScheduleScreen(
                                venueRepo = venueRepo,
                                managerId = currentUserId,
                                managerEmail = userProfile?.email ?: "",
                                onSelectSession = { inspectedSession = it },
                                onEditSession = { editingSession = it },
                                onOpenScoreboard = { matchId -> activeMatchId = matchId },
                                onCancelBooking = { bookingId ->
                                    scope.launch {
                                        runCatching { venueRepo.cancelBooking(currentUserId, bookingId, "Booking cancelled by venue manager") }
                                            .onSuccess { snackMessage = "Booking cancelled & slot freed" }
                                            .onFailure { snackMessage = it.message }
                                    }
                                },
                                onDeleteSession = { sessionId ->
                                    scope.launch {
                                        runCatching { venueRepo.deleteSession(sessionId, currentUserId) }
                                            .onSuccess { snackMessage = "Session slot removed" }
                                    }
                                },
                            )
                            ManagerTab.REQUESTS -> BookingRequestsScreen(
                                venueRepo = venueRepo,
                                managerId = currentUserId,
                                managerEmail = userProfile?.email ?: "",
                                onApprove = { bookingId ->
                                    scope.launch {
                                        runCatching { venueRepo.approveBooking(currentUserId, bookingId, userProfile?.email ?: "") }
                                            .onSuccess { snackMessage = "Booking request approved" }
                                            .onFailure { snackMessage = it.message }
                                    }
                                },
                                onReject = { bookingId, reason ->
                                    scope.launch {
                                        runCatching { venueRepo.rejectBooking(currentUserId, bookingId, reason, userProfile?.email ?: "") }
                                            .onSuccess { snackMessage = "Booking request declined" }
                                            .onFailure { snackMessage = it.message }
                                    }
                                },
                            )
                            ManagerTab.NOTIFICATIONS -> NotificationsScreen(
                                venueRepo = venueRepo,
                                userId = currentUserId,
                                userEmail = userProfile?.email ?: "",
                            )
                            ManagerTab.PROFILE -> ProfileScreen(
                                user = userProfile,
                                role = role,
                                isDarkTheme = isDarkTheme,
                                onToggleTheme = onToggleTheme,
                                onSignOut = performSignOut,
                                onEditProfile = { showEditProfileDialog = true },
                            )
                        }
                    }
                }
            }
        }
    }

    if (inspectedSession != null) {
        val s = inspectedSession!!
        SessionDetailDialog(
            session = s,
            onDismiss = { inspectedSession = null },
            onEdit = if (role == "VENUE_MANAGER") {
                {
                    inspectedSession = null
                    editingSession = s
                }
            } else null,
        )
    }

    if (editingSession != null) {
        EditSessionDialog(
            session = editingSession!!,
            venueRepo = venueRepo,
            managerId = currentUserId,
            onDismiss = { editingSession = null },
            onSave = { sport, date, start, end, cap, prc, coverImage, status ->
                scope.launch {
                    runCatching {
                        venueRepo.updateSession(
                            sessionId = editingSession!!.id,
                            managerId = currentUserId,
                            sport = sport,
                            date = date,
                            startTime = start,
                            endTime = end,
                            capacity = cap,
                            price = prc,
                            coverImage = coverImage,
                            status = status,
                        )
                    }.onSuccess {
                        snackMessage = "Session details and media updated"
                    }.onFailure {
                        snackMessage = it.message ?: "Failed to update session"
                    }
                    editingSession = null
                }
            },
        )
    }

    if (editingVenue != null) {
        EditVenueDialog(
            venue = editingVenue!!,
            onDismiss = { editingVenue = null },
            onSave = { name, type, sports, addr, phone, coverImg ->
                scope.launch {
                    runCatching {
                        venueRepo.updateVenue(
                            venueId = editingVenue!!.id,
                            managerId = currentUserId,
                            name = name,
                            venueType = type,
                            supportedSports = sports,
                            address = addr,
                            phone = phone,
                            coverImage = coverImg,
                        )
                    }.onSuccess {
                        snackMessage = "Venue details updated"
                    }.onFailure {
                        snackMessage = it.message ?: "Failed to update venue"
                    }
                    editingVenue = null
                }
            },
        )
    }

    when {
        activeDialog == "add_venue" -> AddVenueDialog(
            onDismiss = { activeDialog = null },
            onSave = { name, type, sports, addr, phone, coverImg ->
                scope.launch {
                    runCatching {
                        venueRepo.createVenue(
                            managerId = currentUserId,
                            name = name,
                            venueType = type,
                            supportedSports = sports,
                            description = "Professional $type facility with high-intensity floodlighting and equipment.",
                            phone = phone,
                            email = userProfile?.email.orEmpty(),
                            address = addr,
                            area = "City Center",
                            city = "Hyderabad",
                            operatingHours = "Mon-Sun: 6:00 AM - 11:00 PM",
                            facilities = "Parking, Changing Room, Washroom, Drinking Water, Wi-Fi",
                            rules = "Flat sports footwear mandatory.",
                            cancellationPolicy = "Free cancellation 4h before session.",
                            coverImage = coverImg,
                        )
                    }.onSuccess { snackMessage = "Venue created" }.onFailure { snackMessage = it.message }
                    activeDialog = null
                }
            },
        )
        activeDialog == "add_session" -> AddSessionDialog(
            venueRepo = venueRepo,
            managerId = currentUserId,
            managerEmail = userProfile?.email ?: "",
            initialVenueId = preselectedVenueId,
            onDismiss = {
                activeDialog = null
                preselectedVenueId = null
            },
            onSave = { venueId, sport, date, start, end, capacity, price, coverImage ->
                scope.launch {
                    runCatching {
                        venueRepo.createSession(venueId, sport, date, start, end, capacity, price, coverImage)
                    }.onSuccess { snackMessage = "Session slot published to all players" }.onFailure { snackMessage = it.message }
                    activeDialog = null
                    preselectedVenueId = null
                }
            },
        )
        activeDialog == "add_equipment" -> AddEquipmentDialog(
            venueRepo = venueRepo,
            managerId = currentUserId,
            initialVenueId = preselectedVenueId,
            onDismiss = {
                activeDialog = null
                preselectedVenueId = null
            },
            onSave = { venueId, sport, eqName, qty, isFree, charge ->
                scope.launch {
                    runCatching {
                        venueRepo.addEquipment(venueId, sport, eqName, qty, isFree, charge)
                    }.onSuccess { snackMessage = "Equipment added to venue" }.onFailure { snackMessage = it.message }
                    activeDialog = null
                    preselectedVenueId = null
                }
            },
        )
    }
}

// --------------------------------------------------
// AUTHENTICATION & ROLE SELECTION
// --------------------------------------------------

private fun triggerDeviceSecurity(
    activity: FragmentActivity?,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit,
) {
    if (activity == null) {
        onSuccess()
        return
    }
    val biometricManager = BiometricManager.from(activity)
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val canAuth = biometricManager.canAuthenticate(authenticators)
    if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
        val executor = ContextCompat.getMainExecutor(activity)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Device Security Verification")
            .setSubtitle("Confirm your fingerprint or device PIN/pattern to access PlayZone")
            .setAllowedAuthenticators(authenticators)
            .build()

        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onFailure(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
            }
        })
        prompt.authenticate(promptInfo)
    } else {
        onSuccess()
    }
}

@Composable
private fun GoogleAuthScreen(
    venueRepo: VenueBookingRepository,
    onContinue: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()
    var isSigningIn by remember { mutableStateOf(false) }
    var accountChooserSheet by remember { mutableStateOf(false) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    var customEmail by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }

    val savedUsers by venueRepo.observeAllUsers().collectAsStateWithLifecycle(emptyList())

    val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.ifBlank { "487952330617-qcp1kenpv91p68k9p3jr6ustdjmduug2.apps.googleusercontent.com" }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        isSigningIn = false
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account: GoogleSignInAccount? = task.getResult(ApiException::class.java)
            if (account != null && !account.email.isNullOrBlank()) {
                val email: String = account.email!!
                val name: String = account.displayName ?: email.substringBefore("@").replace(".", " ")

                // Authenticate with Firebase Auth
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                FirebaseAuth.getInstance().signInWithCredential(credential)
                    .addOnCompleteListener { _ ->
                        // Enforce real Android device security verification before granting access
                        triggerDeviceSecurity(
                            activity = activity,
                            onSuccess = {
                                authErrorMessage = null
                                onContinue(email, name)
                            },
                            onFailure = { err ->
                                isSigningIn = false
                                authErrorMessage = "Device verification required: $err"
                            },
                        )
                    }
            } else {
                accountChooserSheet = true
            }
        } catch (e: Exception) {
            accountChooserSheet = true
        }
    }

    Surface(Modifier.fillMaxSize(), color = PlayZoneDeepNavy) {
        Box(Modifier.fillMaxSize()) {
            Image(
                painterResource(R.drawable.login_hero_bg),
                contentDescription = "Sports Arena Atmosphere",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Smooth luxury gradient preserving the dramatic stadium photography
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            PlayZoneDeepSpruce.copy(alpha = 0.35f),
                            PlayZoneDeepSpruce.copy(alpha = 0.95f),
                        ),
                    ),
                ),
            )
            Column(
                Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 32.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.Start,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, PlayZoneChampagneGold.copy(alpha = 0.6f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(68.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.playzone_logo),
                        contentDescription = "PlayZone Brand Logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("PlayZone", style = MaterialTheme.typography.displayLarge, color = Color.White, fontWeight = FontWeight.Black)
                Text(
                    "Play More. Together.",
                    style = MaterialTheme.typography.headlineMedium,
                    color = PlayZoneChampagneGold,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Discover sports arenas, book sessions, manage venue operating hours, and track live scoreboards.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PlayZoneAlabaster,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                )

                if (authErrorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = PlayZoneErrorRed.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PlayZoneErrorRed),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Security, contentDescription = null, tint = PlayZoneErrorRed, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(authErrorMessage!!, style = MaterialTheme.typography.bodySmall, color = Color.White)
                        }
                    }
                }

                PressButton(
                    onClick = {
                        if (isSigningIn) return@PressButton
                        isSigningIn = true
                        authErrorMessage = null
                        scope.launch {
                            runCatching {
                                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                    .requestIdToken(clientId)
                                    .requestEmail()
                                    .build()
                                val googleSignInClient = GoogleSignIn.getClient(context, gso)
                                // Clear previously selected account so system account picker is shown
                                googleSignInClient.signOut().addOnCompleteListener {
                                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                }
                            }.onFailure {
                                isSigningIn = false
                                accountChooserSheet = true
                            }
                        }
                    },
                    enabled = !isSigningIn,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PlayZoneChampagneGold, contentColor = PlayZoneDeepSpruce),
                ) {
                    if (isSigningIn) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = PlayZoneDeepSpruce, strokeWidth = 2.5.dp)
                    } else {
                        Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = PlayZoneDeepSpruce)
                        Spacer(Modifier.width(10.dp))
                        Text("Continue with Google", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = PlayZoneDeepSpruce)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (accountChooserSheet) {
        AlertDialog(
            onDismissRequest = { accountChooserSheet = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = PlayZonePrimaryCoral)
                    Spacer(Modifier.width(8.dp))
                    Text("Select Google Account", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (savedUsers.isNotEmpty()) {
                        Text(
                            "CONFIGURED ACCOUNTS ON THIS DEVICE",
                            style = MaterialTheme.typography.labelSmall,
                            color = PlayZonePrimaryCoral,
                            fontWeight = FontWeight.Bold,
                        )
                        savedUsers.forEach { user ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    triggerDeviceSecurity(
                                        activity = activity,
                                        onSuccess = {
                                            accountChooserSheet = false
                                            authErrorMessage = null
                                            onContinue(user.email, user.name)
                                        },
                                        onFailure = { err ->
                                            accountChooserSheet = false
                                            authErrorMessage = "Device verification required: $err"
                                        },
                                    )
                                },
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = CircleShape, color = PlayZoneDeepNavy, modifier = Modifier.size(36.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(user.name.take(1).uppercase(), color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                        Text(user.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (user.role.isNotBlank()) {
                                        Surface(shape = CircleShape, color = PlayZoneDeepNavy) {
                                            Text(
                                                user.role.replace('_', ' '),
                                                Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = PlayZonePeachLight,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    Text(
                        if (savedUsers.isEmpty()) "Sign in with your Google account on this device:" else "Or sign in with another Google account:",
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    OutlinedTextField(
                        value = customEmail,
                        onValueChange = { customEmail = it },
                        label = { Text("Google Account Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (customEmail.isNotBlank()) {
                            val emailToUse = customEmail.trim()
                            val nameToUse = customName.ifBlank { emailToUse.substringBefore("@").replace(".", " ") }
                            triggerDeviceSecurity(
                                activity = activity,
                                onSuccess = {
                                    accountChooserSheet = false
                                    authErrorMessage = null
                                    onContinue(emailToUse, nameToUse)
                                },
                                onFailure = { err ->
                                    accountChooserSheet = false
                                    authErrorMessage = "Device verification required: $err"
                                },
                            )
                        }
                    },
                    enabled = customEmail.isNotBlank() && customEmail.contains("@"),
                ) {
                    Text("Verify & Sign In", color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { accountChooserSheet = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun RoleSelectionScreen(onRoleChosen: (String) -> Unit) {
    var selected by remember { mutableStateOf("PLAYER") }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Text("How will you use PlayZone?", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
            Text(
                "Choose your role to configure your personalized workspace.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
            )

            RoleCard(
                title = "Venue Manager",
                description = "Publish slots, manage sports arenas, and approve player bookings.",
                icon = Icons.Rounded.AddBusiness,
                isSelected = selected == "VENUE_MANAGER",
                onClick = { selected = "VENUE_MANAGER" },
            )
            Spacer(Modifier.height(16.dp))

            RoleCard(
                title = "Sports Player",
                description = "Discover nearby arenas, reserve slots, and control live match scoreboards.",
                icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                isSelected = selected == "PLAYER",
                onClick = { selected = "PLAYER" },
            )

            Spacer(Modifier.height(36.dp))
            PressButton(
                onClick = { onRoleChosen(selected) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PlayZoneChampagneGold, contentColor = PlayZoneDeepSpruce),
            ) {
                Text("Continue to Workspace", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = PlayZoneDeepSpruce)
            }
        }
    }
}

@Composable
private fun RoleCard(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) PlayZoneChampagneGold else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) PlayZoneBrandPurple else PlayZoneDeepSpruce.copy(alpha = 0.1f),
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else PlayZoneChampagneGold,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isSelected) {
                Surface(shape = CircleShape, color = PlayZoneChampagneGold, modifier = Modifier.size(24.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = PlayZoneDeepSpruce, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingScreen(role: String, userName: String, userEmail: String = "", onComplete: () -> Unit, venueRepo: VenueBookingRepository, currentUserId: String) {
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(26.dp)) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = PlayZoneBrandPurple.copy(alpha = 0.15f),
                        modifier = Modifier.size(56.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (role == "VENUE_MANAGER") Icons.Rounded.AddBusiness else Icons.Rounded.SportsTennis,
                                contentDescription = null,
                                tint = PlayZoneBrandPurple,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        if (role == "VENUE_MANAGER") "Welcome, Arena Manager" else "Welcome, $userName",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        if (role == "VENUE_MANAGER") "Your arena operations dashboard is ready. Add your contact phone to link booking alerts and start managing your venue." else "Your sports player profile is ready. Explore nearby venues, request booking slots, and track live scoreboards.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 24.dp),
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Contact Phone (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                    )

                    Spacer(Modifier.height(28.dp))
                    PressButton(
                        onClick = {
                            scope.launch {
                                if (phone.isNotBlank()) {
                                    val current = venueRepo.getUser(currentUserId)
                                    if (current != null) {
                                        venueRepo.saveUserRole(
                                            userId = currentUserId,
                                            googleAccountId = current.googleAccountId,
                                            name = current.name,
                                            email = current.email,
                                            role = role,
                                            phone = phone,
                                        )
                                    }
                                }
                                onComplete()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PlayZoneChampagneGold, contentColor = PlayZoneDeepSpruce),
                    ) {
                        Text(
                            if (role == "VENUE_MANAGER") "Open Manager Dashboard" else "Start Exploring",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PlayZoneDeepSpruce,
                        )
                    }
                }
            }
        }
    }
}

// --------------------------------------------------
// PLAYER EXPERIENCE SCREENS
// --------------------------------------------------

@Composable
private fun PlayerHomeScreen(
    venueRepo: VenueBookingRepository,
    userName: String,
    onSelectVenue: (String) -> Unit,
    onOpenBookings: () -> Unit,
    onOpenScoreboard: (String) -> Unit,
) {
    val venues by venueRepo.observeAllVenues().collectAsStateWithLifecycle(emptyList())
    val liveScoreboards by venueRepo.observeLiveScoreboards().collectAsStateWithLifecycle(emptyList())
    var selectedSport by remember { mutableStateOf("All") }

    val filteredVenues = remember(venues, selectedSport) {
        if (selectedSport == "All") venues else venues.filter { it.supportedSports.contains(selectedSport, ignoreCase = true) }
    }

    val activeLiveMatches = remember(liveScoreboards) {
        liveScoreboards.filter {
            it.status == "LIVE" && (it.scoreA > 0 || it.scoreB > 0 || (it.detailText.isNotBlank() && it.detailText != "0 - 0" && it.detailText != "0/0 (0.0 ov)"))
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            // Sports Status Header Bar
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = PlayZoneDeepNavy) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(8.dp), shape = CircleShape, color = PlayZonePrimaryCoral) {}
                        Spacer(Modifier.width(6.dp))
                        Text("Active Season", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text("Verified Arenas", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryText)
                    }
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text("Instant Slots", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryText)
                    }
                }
            }
        }

        item {
            Column {
                Text("PLAYZONE SPORTS DISCOVERY", style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)
                Text("Hello, $userName 👋", style = MaterialTheme.typography.displayMedium, color = PlayZonePrimaryText, fontWeight = FontWeight.Bold)
                Text("Discover sports arenas and book your session easily.", style = MaterialTheme.typography.bodyMedium, color = PlayZoneSecondaryText)
            }
        }

        // PlayZone Hero Banner with authentic turf photography
        item {
            Surface(shape = MaterialTheme.shapes.large, color = PlayZoneDeepNavy, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(180.dp)) {
                    Image(
                        painterResource(R.drawable.venue_turf),
                        contentDescription = "Sports Arena Action",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(
                                listOf(PlayZoneDeepNavy.copy(alpha = 0.95f), PlayZoneDeepNavy.copy(alpha = 0.65f), Color.Transparent),
                            ),
                        ),
                    )
                    Column(
                        Modifier.align(Alignment.CenterStart).padding(20.dp).fillMaxHeight(),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("Your Venue.\nYour Community.", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Host, book and play with ease.", style = MaterialTheme.typography.bodyMedium, color = PlayZoneSoftCream)
                        Spacer(Modifier.height(12.dp))
                        PressButton(onClick = onOpenBookings, colors = ButtonDefaults.buttonColors(containerColor = PlayZonePrimaryCoral, contentColor = Color.White)) {
                            Text("Book Session", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        if (activeLiveMatches.isNotEmpty()) {
            item {
                val live = activeLiveMatches.first()
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().clickable { onOpenScoreboard(live.id) },
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(10.dp), shape = CircleShape, color = MaterialTheme.colorScheme.error) {}
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("LIVE SCOREBOARD  ·  ${live.sport.uppercase()}", style = MaterialTheme.typography.labelSmall, color = PlayZoneDeepNavy, fontWeight = FontWeight.Bold)
                            Text("${live.teamA} vs ${live.teamB}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(live.detailText, style = MaterialTheme.typography.bodyMedium)
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                    }
                }
            }
        }

        item {
            Text("POPULAR SPORTS", style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val sports = listOf("All", "Cricket", "Football", "Badminton", "Basketball", "Tennis", "Volleyball")
                items(sports) { sport ->
                    val isSelected = selectedSport.equals(sport, ignoreCase = true)
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) PlayZoneDeepNavy else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { selectedSport = sport },
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (sport != "All") {
                                Icon(
                                    when (sport) {
                                        "Football" -> Icons.Rounded.SportsSoccer
                                        "Cricket" -> Icons.Rounded.SportsCricket
                                        "Tennis" -> Icons.Rounded.SportsTennis
                                        "Basketball" -> Icons.Rounded.SportsBasketball
                                        "Volleyball" -> Icons.Rounded.SportsVolleyball
                                        else -> Icons.Rounded.Sports
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSelected) PlayZonePrimaryCoral else PlayZonePrimaryText,
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                sport,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else PlayZonePrimaryText,
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (selectedSport == "All") "FEATURED ARENAS" else "$selectedSport ARENAS (${filteredVenues.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = PlayZonePrimaryCoral,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        items(filteredVenues, key = { it.id }) { venue ->
            VenueCard(venue = venue, onClick = { onSelectVenue(venue.id) })
        }
    }
}

@Composable
private fun ExploreVenuesScreen(venueRepo: VenueBookingRepository, onSelectVenue: (String) -> Unit) {
    val venues by venueRepo.observeAllVenues().collectAsStateWithLifecycle(emptyList())
    var searchQuery by remember { mutableStateOf("") }

    val filtered = venues.filter {
        searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) || it.supportedSports.contains(searchQuery, ignoreCase = true) || it.area.contains(searchQuery, ignoreCase = true)
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Discover Sports Arenas", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search venue, sport, or area...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        items(filtered, key = { it.id }) { venue ->
            VenueCard(venue = venue, onClick = { onSelectVenue(venue.id) })
        }
    }
}

private fun venueDrawableRes(coverImage: String, sport: String = ""): Int {
    val clean = (if (coverImage.isNotBlank()) coverImage else sport).lowercase()
    return when {
        clean.contains("cricket") -> R.drawable.venue_cricket
        clean.contains("foot") || clean.contains("soccer") -> R.drawable.venue_football
        clean.contains("badminton") -> R.drawable.venue_badminton
        clean.contains("basket") -> R.drawable.venue_basketball
        clean.contains("tennis") && !clean.contains("table") -> R.drawable.venue_tennis
        clean.contains("volley") -> R.drawable.venue_volleyball
        clean.contains("table") || clean.contains("ping") -> R.drawable.venue_table_tennis
        clean.contains("swim") || clean.contains("pool") || clean.contains("aqua") -> R.drawable.venue_swimming
        clean.contains("turf") -> R.drawable.venue_turf
        else -> when {
            sport.contains("Cricket", true) -> R.drawable.venue_cricket
            sport.contains("Football", true) -> R.drawable.venue_football
            sport.contains("Badminton", true) -> R.drawable.venue_badminton
            sport.contains("Basketball", true) -> R.drawable.venue_basketball
            sport.contains("Tennis", true) -> R.drawable.venue_tennis
            sport.contains("Volleyball", true) -> R.drawable.venue_volleyball
            else -> R.drawable.venue_turf
        }
    }
}

@Composable
private fun VenueCardImage(
    coverImage: String,
    sport: String = "",
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uri = remember(coverImage) {
        if (coverImage.startsWith("content://") || coverImage.startsWith("file://")) Uri.parse(coverImage) else null
    }

    if (uri != null) {
        val bitmap = remember(uri) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = modifier,
            )
            return
        }
    }

    Image(
        painter = painterResource(venueDrawableRes(coverImage, sport)),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

@Composable
private fun VenueCard(venue: VenueEntity, onClick: () -> Unit) {
    PressCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large as RoundedCornerShape,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(150.dp)) {
                VenueCardImage(
                    coverImage = venue.coverImage,
                    sport = venue.supportedSports,
                    contentDescription = venue.name,
                    modifier = Modifier.fillMaxSize(),
                )
                Surface(
                    shape = CircleShape,
                    color = PlayZoneDeepNavy.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                ) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Star, contentDescription = null, tint = PlayZonePrimaryCoral, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("${venue.rating}", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PlayZoneDeepNavy.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                ) {
                    Text(
                        venue.supportedSports.split(",").firstOrNull()?.trim() ?: venue.supportedSports,
                        Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = PlayZonePrimaryCoral,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    venue.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = PlayZoneSecondaryText, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${venue.area}, ${venue.city}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PlayZoneSecondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = PlayZoneSuccessGreen, modifier = Modifier.size(8.dp)) {}
                        Spacer(Modifier.width(6.dp))
                        Text("Active", style = MaterialTheme.typography.labelSmall, color = PlayZoneSuccessGreen, fontWeight = FontWeight.SemiBold)
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, contentDescription = null, tint = PlayZoneSecondaryText, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: SessionEntity,
    venueName: String? = null,
    venueLocation: String? = null,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    PressCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(72.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(Modifier.fillMaxSize()) {
                    VenueCardImage(
                        coverImage = session.coverImage,
                        sport = session.sport,
                        contentDescription = session.sport,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // If no device image is added, prominently display location badge overlay on the card visual
                    if (session.coverImage.isBlank()) {
                        Surface(
                            shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                            color = PlayZoneDeepSpruce.copy(alpha = 0.90f),
                            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                        ) {
                            Text(
                                venueLocation?.split(",")?.firstOrNull()?.trim() ?: venueName ?: "Location",
                                Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(session.sport.uppercase(), style = MaterialTheme.typography.labelSmall, color = PlayZoneChampagneGold, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (session.status == "AVAILABLE") PlayZoneBrandPurple.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            session.status,
                            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = if (session.status == "AVAILABLE") PlayZoneBrandPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("${session.startTime}–${session.endTime}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                // Show location details clearly on session card
                val locText = listOfNotNull(venueName?.takeIf { it.isNotBlank() }, venueLocation?.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (locText.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = PlayZoneBrandPurple, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            locText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Text("${session.date}  ·  Max ${session.capacity}  ·  ₹${session.price.toInt()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onEdit != null || onDelete != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onEdit != null) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit Session", tint = PlayZoneChampagneGold, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete Session", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            } else {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SessionDetailDialog(
    session: SessionEntity,
    venueName: String? = null,
    venueLocation: String? = null,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onBookSlot: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(160.dp)) {
                    VenueCardImage(
                        coverImage = session.coverImage,
                        sport = session.sport,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (session.status == "AVAILABLE") PlayZoneDeepNavy else PlayZonePrimaryCoral,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    ) {
                        Text(
                            session.status,
                            Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (session.coverImage.isBlank() && (!venueName.isNullOrBlank() || !venueLocation.isNullOrBlank())) {
                        Surface(
                            shape = RoundedCornerShape(topEnd = 8.dp),
                            color = PlayZoneDeepNavy.copy(alpha = 0.9f),
                            modifier = Modifier.align(Alignment.BottomStart),
                        ) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = PlayZonePrimaryCoral, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    listOfNotNull(venueName, venueLocation).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }

                Text("${session.sport.uppercase()} MATCH SESSION", style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)
                Text("${session.startTime} – ${session.endTime}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!venueName.isNullOrBlank() || !venueLocation.isNullOrBlank()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Arena Location", style = MaterialTheme.typography.bodySmall, color = PlayZoneSecondaryText)
                                Text(listOfNotNull(venueName, venueLocation).joinToString(", "), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Scheduled Date", style = MaterialTheme.typography.bodySmall, color = PlayZoneSecondaryText)
                            Text(session.date, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Player Capacity", style = MaterialTheme.typography.bodySmall, color = PlayZoneSecondaryText)
                            Text("${session.capacity} Players (${session.bookedCount} Booked)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Session Rate", style = MaterialTheme.typography.bodySmall, color = PlayZoneSecondaryText)
                            Text("₹${session.price.toInt()}", style = MaterialTheme.typography.titleMedium, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text("ARENA RULES & EQUIPMENT", style = MaterialTheme.typography.labelSmall, color = PlayZoneDeepNavy, fontWeight = FontWeight.Bold)
                Text(
                    "Standard match rules apply. Professional LED lighting, boundary dugouts, and verified sport equipment are prepared for this slot.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PlayZoneSecondaryText,
                )

                if (onEdit != null) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onEdit()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit Session Details & Media")
                    }
                }

                if (onBookSlot != null && session.status == "AVAILABLE") {
                    PressButton(
                        onClick = {
                            onDismiss()
                            onBookSlot()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PlayZonePrimaryCoral, contentColor = Color.White),
                    ) {
                        Icon(Icons.Rounded.ConfirmationNumber, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Reserve & Book Slot", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun VenueDetailScreen(
    venueId: String,
    venueRepo: VenueBookingRepository,
    currentUserId: String,
    userName: String,
    userEmail: String,
    userPhone: String,
    onBack: () -> Unit,
    onBookingSubmitted: (String) -> Unit,
) {
    val venue by venueRepo.observeVenue(venueId).collectAsStateWithLifecycle(null)
    val equipmentList by venueRepo.observeEquipment(venueId).collectAsStateWithLifecycle(emptyList())
    val todayStr = remember { venueRepo.getIndianStandardToday() }
    val allVenueSessions by venueRepo.observeVenueSessions(venueId).collectAsStateWithLifecycle(emptyList())
    val availableDates = remember(allVenueSessions, todayStr) {
        (listOf(todayStr) + allVenueSessions.map { it.date }).distinct().sorted()
    }
    var selectedDate by remember { mutableStateOf(todayStr) }
    val sessions = remember(allVenueSessions, selectedDate) {
        allVenueSessions.filter { it.date == selectedDate }
    }
    val reviews by venueRepo.observeReviews(venueId).collectAsStateWithLifecycle(emptyList())

    var isCustomSlot by remember { mutableStateOf(false) }
    var customStartTime by remember { mutableStateOf("06:00 PM") }
    var customEndTime by remember { mutableStateOf("07:00 PM") }
    var customSport by remember { mutableStateOf("") }
    var selectedSessionId by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf("") }
    var mySquadName by remember(userName) { mutableStateOf(userName.ifBlank { "My Squad" }) }
    var opponentSquadName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    if (venue == null) return

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(220.dp)) {
                VenueCardImage(
                    coverImage = venue!!.coverImage,
                    sport = venue!!.supportedSports,
                    contentDescription = venue!!.name,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.85f))),
                    ),
                )
                IconButton(onClick = onBack, modifier = Modifier.padding(12.dp).align(Alignment.TopStart)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                    Surface(shape = CircleShape, color = PrimaryPlum) {
                        Text(venue!!.venueType, Modifier.padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = PeachAccent)
                    }
                    Text(venue!!.name, style = MaterialTheme.typography.displayMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("${venue!!.address}, ${venue!!.area}", style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("ABOUT VENUE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(venue!!.description, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(8.dp))
                Text("Operating Hours: ${venue!!.operatingHours}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Map Location Card
        item {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = PrimaryPlum, modifier = Modifier.size(38.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Place, contentDescription = null, tint = PeachAccent)
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("LOCATION & MAP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text("${venue!!.address}, ${venue!!.area}, ${venue!!.city}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(onClick = {}) {
                        Text("Directions")
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("FACILITIES INCLUDED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(venue!!.facilities.split(",")) { fac ->
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(fac.trim(), Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (equipmentList.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("EQUIPMENT AVAILABLE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    equipmentList.forEach { eq ->
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box {
                                        Surface(shape = CircleShape, color = PrimaryPlum, modifier = Modifier.size(42.dp)) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    when (eq.sport) {
                                                        "Cricket" -> Icons.Rounded.SportsCricket
                                                        "Football" -> Icons.Rounded.SportsSoccer
                                                        "Tennis" -> Icons.Rounded.SportsTennis
                                                        "Basketball" -> Icons.Rounded.SportsBasketball
                                                        "Volleyball" -> Icons.Rounded.SportsVolleyball
                                                        else -> Icons.Rounded.Sports
                                                    },
                                                    contentDescription = null,
                                                    tint = PeachAccent,
                                                    modifier = Modifier.size(22.dp),
                                                )
                                            }
                                        }
                                        Surface(
                                            shape = CircleShape,
                                            color = PeachAccent,
                                            modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp),
                                        ) {
                                            Text(
                                                "${eq.quantity}",
                                                Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PrimaryPlum,
                                                fontWeight = FontWeight.Black,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column {
                                        Text(eq.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("${eq.quantity} units for ${eq.sport}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Surface(shape = CircleShape, color = if (eq.isIncludedFree) PrimaryPlum else MaterialTheme.colorScheme.secondaryContainer) {
                                    Text(
                                        if (eq.isIncludedFree) "Included Free" else "₹${eq.additionalCharge.toInt()} extra",
                                        Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (eq.isIncludedFree) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("TIME SLOTS ($selectedDate)", style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        isCustomSlot = !isCustomSlot
                        if (isCustomSlot) selectedSessionId = null
                    }) {
                        Icon(if (isCustomSlot) Icons.Rounded.Check else Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isCustomSlot) "Choose Scheduled Slot" else "Request Custom Slot", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (availableDates.size > 1) {
                    Spacer(Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(availableDates) { d ->
                            FilterChip(
                                selected = selectedDate == d,
                                onClick = {
                                    selectedDate = d
                                    selectedSessionId = null
                                },
                                label = { Text(if (d == todayStr) "Today ($d)" else d) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PlayZonePrimaryCoral,
                                    selectedLabelColor = Color.White,
                                ),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (!isCustomSlot) {
                    if (sessions.isEmpty()) {
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text("No open session slots posted for $selectedDate.", style = MaterialTheme.typography.bodyMedium, color = PlayZoneSecondaryText)
                                Spacer(Modifier.height(6.dp))
                                PressButton(onClick = { isCustomSlot = true }) {
                                    Text("Request a Custom Time Slot for this Venue")
                                }
                            }
                        }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(sessions, key = { it.id }) { session ->
                                val isAvailable = session.status == "AVAILABLE"
                                FilterChip(
                                    selected = selectedSessionId == session.id,
                                    onClick = { if (isAvailable) selectedSessionId = session.id },
                                    enabled = isAvailable,
                                    label = { Text("${session.sport}: ${session.startTime}–${session.endTime} (₹${session.price.toInt()})") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PlayZonePrimaryCoral,
                                        selectedLabelColor = Color.White,
                                    ),
                                )
                            }
                        }
                        val selectedSession = sessions.find { it.id == selectedSessionId }
                        if (selectedSession != null) {
                            Spacer(Modifier.height(8.dp))
                            SessionCard(
                                session = selectedSession,
                                venueName = venue!!.name,
                                venueLocation = "${venue!!.address}, ${venue!!.area}",
                                onClick = {},
                            )
                        }
                    }
                } else {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("REQUEST CUSTOM TIME & SPORT", style = MaterialTheme.typography.labelSmall, color = PlayZoneDeepNavy, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = customStartTime,
                                    onValueChange = { customStartTime = it },
                                    label = { Text("Start Time") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = customEndTime,
                                    onValueChange = { customEndTime = it },
                                    label = { Text("End Time") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                )
                            }
                            OutlinedTextField(
                                value = customSport,
                                onValueChange = { customSport = it },
                                label = { Text("Sport") },
                                placeholder = { Text(venue!!.supportedSports.split(",").firstOrNull()?.trim() ?: "Football") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                        }
                    }
                }
            }
        }

        if (selectedSessionId != null || isCustomSlot) {
            item {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("BOOKING CONFIRMATION SUMMARY", style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)
                        Text("Player: $userName ($userEmail)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Venue: ${venue!!.name} (${venue!!.address}, ${venue!!.city})", style = MaterialTheme.typography.bodySmall)
                        Surface(shape = MaterialTheme.shapes.small, color = PlayZoneDeepNavy) {
                            Text(
                                "Target Venue Manager: Request will be sent directly to ${venue!!.email}",
                                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                        }
                        OutlinedTextField(
                            value = mySquadName,
                            onValueChange = { mySquadName = it },
                            label = { Text("Your Squad / Team Name") },
                            placeholder = { Text("e.g. Red Dragons or Striker Squad") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = opponentSquadName,
                            onValueChange = { opponentSquadName = it },
                            label = { Text("Opponent Squad / Team Name (Optional)") },
                            placeholder = { Text("e.g. Phoenix FC or Friendly Squad") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Location specifics / match notes") },
                            placeholder = { Text("e.g. Ground 2, friendly kickoff") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PressButton(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        val bookingDate = sessions.find { it.id == selectedSessionId }?.date ?: selectedDate
                                        venueRepo.submitBookingRequest(
                                            playerId = currentUserId,
                                            playerName = userName,
                                            playerEmail = userEmail,
                                            playerPhone = userPhone,
                                            playerCount = 4,
                                            venueId = venue!!.id,
                                            sessionId = selectedSessionId ?: "CUSTOM_${UUID.randomUUID().toString().take(6)}",
                                            equipmentRequested = "Standard Equipment",
                                            notes = notes,
                                            customDate = bookingDate,
                                            customStartTime = customStartTime,
                                            customEndTime = customEndTime,
                                            customSport = customSport.ifBlank { venue!!.supportedSports.split(",").first().trim() },
                                            squadName = mySquadName,
                                            opponentSquadName = opponentSquadName,
                                        )
                                    }.onSuccess { booking ->
                                        onBookingSubmitted(booking.bookingReference)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                        ) {
                            Text(if (isCustomSlot) "Send Custom Slot Request to Manager" else "Send Booking Request", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerBookingsScreen(
    venueRepo: VenueBookingRepository,
    playerId: String,
    playerEmail: String = "",
    onOpenScoreboard: (String) -> Unit,
    onCancelBooking: (String) -> Unit,
    onExploreVenues: () -> Unit = {},
) {
    val bookings by venueRepo.observePlayerBookings(playerId, playerEmail).collectAsStateWithLifecycle(emptyList())
    var selectedFilter by remember { mutableStateOf("ACTIVE") } // ACTIVE, ALL, HISTORY

    val activeBookings = remember(bookings) { bookings.filter { it.status == "CONFIRMED" || it.status == "PENDING" } }
    val historyBookings = remember(bookings) { bookings.filter { it.status == "CANCELLED" || it.status == "REJECTED" } }

    val displayedBookings = when (selectedFilter) {
        "ACTIVE" -> activeBookings
        "HISTORY" -> historyBookings
        else -> bookings
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column {
                Text("RESERVATIONS & PASSES", style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)
                Text("My Bookings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedFilter == "ACTIVE",
                    onClick = { selectedFilter = "ACTIVE" },
                    label = { Text("Active (${activeBookings.size})") },
                )
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All (${bookings.size})") },
                )
                FilterChip(
                    selected = selectedFilter == "HISTORY",
                    onClick = { selectedFilter = "HISTORY" },
                    label = { Text("Past / Cancelled (${historyBookings.size})") },
                )
            }
        }

        if (displayedBookings.isEmpty()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(shape = CircleShape, color = PlayZonePrimaryCoral.copy(alpha = 0.15f), modifier = Modifier.size(54.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.ConfirmationNumber, contentDescription = null, tint = PlayZonePrimaryCoral, modifier = Modifier.size(28.dp))
                            }
                        }
                        Text(
                            if (selectedFilter == "ACTIVE") "No active bookings" else "No booking records found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Discover sports arenas in your city, check session availability, and reserve your spot instantly.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PressButton(
                            onClick = onExploreVenues,
                            modifier = Modifier.padding(top = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PlayZonePrimaryCoral, contentColor = Color.White),
                        ) {
                            Icon(Icons.Rounded.Explore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Explore Arenas & Book", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        items(displayedBookings, key = { it.id }) { booking ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(booking.sport.uppercase(), style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)
                        Surface(
                            shape = CircleShape,
                            color = when (booking.status) {
                                "CONFIRMED" -> PlayZoneSuccessGreen
                                "PENDING" -> PlayZonePrimaryCoral
                                "REJECTED" -> MaterialTheme.colorScheme.error
                                else -> Color.Gray
                            },
                        ) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    when (booking.status) {
                                        "CONFIRMED" -> Icons.Rounded.CheckCircle
                                        "PENDING" -> Icons.Rounded.Notifications
                                        "REJECTED" -> Icons.Rounded.Close
                                        else -> Icons.Rounded.Cancel
                                    },
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    when (booking.status) {
                                        "CONFIRMED" -> "CONFIRMED"
                                        "PENDING" -> "WAITING APPROVAL"
                                        "REJECTED" -> "DECLINED"
                                        else -> "CANCELLED"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    Text(booking.venueName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${booking.date}  ·  ${booking.startTime}–${booking.endTime}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("Ref: #${booking.bookingReference}  ·  Players: ${booking.playerCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (booking.status == "REJECTED" && booking.rejectionReason.isNotBlank()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "Decline note: ${booking.rejectionReason}",
                                Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }

                    if (booking.status == "CANCELLED" && booking.cancellationReason.isNotBlank()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "Cancellation info: ${booking.cancellationReason}",
                                Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (booking.status == "PENDING") {
                        Text(
                            "Booking request has been sent to the venue manager. You will receive an instant notification as soon as it is reviewed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (booking.status == "CONFIRMED") {
                            PressButton(
                                onClick = { onOpenScoreboard("MATCH-${booking.id}") },
                                colors = ButtonDefaults.buttonColors(containerColor = PlayZonePrimaryCoral, contentColor = Color.White),
                            ) {
                                Icon(Icons.Rounded.SportsScore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open Live Scoreboard", fontWeight = FontWeight.Bold)
                            }
                        }
                        if (booking.status == "PENDING" || booking.status == "CONFIRMED") {
                            OutlinedButton(
                                onClick = { onCancelBooking(booking.id) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) {
                                Text(if (booking.status == "PENDING") "Cancel Request" else "Cancel Booking")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveMatchesListScreen(venueRepo: VenueBookingRepository, onSelectMatch: (String) -> Unit) {
    val liveScoreboards by venueRepo.observeLiveScoreboards().collectAsStateWithLifecycle(emptyList())

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Live Scoreboards", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }

        if (liveScoreboards.isEmpty()) {
            item { EmptyCard("No live matches", "Live scoreboards for confirmed sessions will appear here.") }
        }

        items(liveScoreboards, key = { it.id }) { match ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().clickable { onSelectMatch(match.id) },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(8.dp), shape = CircleShape, color = MaterialTheme.colorScheme.error) {}
                            Spacer(Modifier.width(6.dp))
                            Text(match.sport.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("${match.teamA} vs ${match.teamB}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(match.detailText, style = MaterialTheme.typography.bodyLarge, color = PrimaryPlum)
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun LiveScoreboardScreen(
    matchId: String,
    venueRepo: VenueBookingRepository,
    currentUserId: String,
    currentUserEmail: String = "",
    onBack: () -> Unit,
    onScoreUpdated: () -> Unit,
) {
    val match by venueRepo.observeScoreboard(matchId).collectAsStateWithLifecycle(null)
    val events by venueRepo.observeScoreboardEvents(matchId).collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()

    val m = match ?: return
    var showEditTeamsDialog by remember { mutableStateOf(false) }
    var editTeamA by remember(m.teamA) { mutableStateOf(m.teamA) }
    var editTeamB by remember(m.teamB) { mutableStateOf(m.teamB) }
    val booking by venueRepo.observeBooking(m.bookingId).collectAsStateWithLifecycle(null)
    val venue by venueRepo.observeVenue(m.venueId).collectAsStateWithLifecycle(null)

    val isBookedPlayer = booking != null && (
        booking?.playerId == currentUserId || 
        (currentUserEmail.isNotBlank() && booking?.playerEmail.equals(currentUserEmail, ignoreCase = true))
    )
    val isVenueManager = (venue != null && (venue?.managerId == currentUserId || (currentUserEmail.isNotBlank() && venue?.email.equals(currentUserEmail, ignoreCase = true)))) || currentUserId.startsWith("mgr_") || currentUserId == "mgr_ps22_demo"
    val canControl = isBookedPlayer || isVenueManager || m.controllerUserId == currentUserId || (currentUserEmail.isNotBlank() && m.controllerUserId.contains(currentUserEmail.hashCode().toString()))

    if (showEditTeamsDialog) {
        AlertDialog(
            onDismissRequest = { showEditTeamsDialog = false },
            title = { Text("Edit Squad Names", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Update the squad/team names shown on the live scoreboard and broadcast feed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = editTeamA,
                        onValueChange = { editTeamA = it },
                        label = { Text("Team / Squad A") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editTeamB,
                        onValueChange = { editTeamB = it },
                        label = { Text("Team / Squad B") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            venueRepo.updateMatchTeams(
                                userId = currentUserId,
                                matchId = matchId,
                                teamA = editTeamA,
                                teamB = editTeamB,
                                userEmail = currentUserEmail,
                            )
                            onScoreUpdated()
                            showEditTeamsDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PlayZonePrimaryCoral),
                ) {
                    Text("Save Squads", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditTeamsDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                Text("MATCH SCOREBOARD", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = if (canControl) PlayZonePrimaryCoral else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (canControl) Icons.Rounded.Edit else Icons.Rounded.Visibility,
                            contentDescription = null,
                            tint = if (canControl) Color.White else PlayZoneDeepNavy,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (canControl) "Authorized Scorer" else "Spectator (View Only)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (canControl) Color.White else PlayZoneDeepNavy,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        if (!canControl) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = PlayZoneDeepNavy, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("SPECTATOR VIEW-ONLY BROADCAST", style = MaterialTheme.typography.labelSmall, color = PlayZoneDeepNavy, fontWeight = FontWeight.Bold)
                            Text("Official scorer: ${m.teamA}. Live scores are updating in real time.", style = MaterialTheme.typography.bodySmall, color = PlayZoneSecondaryText)
                        }
                    }
                }
            }
        }

        // Header Scorecard
        item {
            Surface(shape = MaterialTheme.shapes.large, color = PrimaryPlum, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            Modifier.size(8.dp),
                            shape = CircleShape,
                            color = when (m.status) {
                                "LIVE" -> MaterialTheme.colorScheme.error
                                "SCHEDULED" -> PeachAccent
                                else -> Color.Gray
                            },
                        ) {}
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when (m.status) {
                                "LIVE" -> "LIVE MATCH  ·  ${m.sport.uppercase()}"
                                "SCHEDULED" -> "UPCOMING MATCH  ·  ${m.sport.uppercase()}"
                                else -> "MATCH COMPLETED  ·  ${m.sport.uppercase()}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = PeachAccent,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(m.teamA, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("${m.scoreA}", style = MaterialTheme.typography.displayLarge, color = PeachAccent, fontWeight = FontWeight.Black)
                        }
                        Text("VS", style = MaterialTheme.typography.titleMedium, color = Color.LightGray)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(m.teamB, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("${m.scoreB}", style = MaterialTheme.typography.displayLarge, color = PeachAccent, fontWeight = FontWeight.Black)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (m.status == "SCHEDULED") "Match Scheduled (Not Started)" else m.detailText,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    if (canControl) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.clickable {
                                editTeamA = m.teamA
                                editTeamB = m.teamB
                                showEditTeamsDialog = true
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Edit,
                                    contentDescription = null,
                                    tint = PeachAccent,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Edit Squad Names",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Start Match Banner for Scheduled Matches
        if (m.status == "SCHEDULED") {
            item {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("MATCH NOT STARTED YET", style = MaterialTheme.typography.labelSmall, color = PrimaryPlum)
                        if (canControl) {
                            Text("You are the authorized player for this booking. Tap below when you arrive at the arena to start kickoff and activate live scoring.", style = MaterialTheme.typography.bodyMedium)
                            PressButton(
                                onClick = {
                                    scope.launch {
                                        venueRepo.startMatch(currentUserId, matchId, currentUserEmail)
                                        onScoreUpdated()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Start Kickoff & Activate Scorecard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text("Waiting for the booking owner (${m.teamA}) to arrive at the venue and start the match scorecard.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Cricbuzz Style Player Stats Card (Batters & Bowlers / Goalscorers) - Only when Live or Completed
        if (m.status != "SCHEDULED") {
            item {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ACTIVE PLAYERS ON PITCH", style = MaterialTheme.typography.labelSmall, color = PrimaryPlum)
                        if (m.sport == "Cricket") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Rahul Sharma*", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("45 (28b)  ·  5x4 2x6  ·  SR 160.7", style = MaterialTheme.typography.bodySmall, color = PrimaryPlum)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Noah Vale", style = MaterialTheme.typography.bodyMedium)
                                Text("18 (12b)  ·  2x4 0x6  ·  SR 150.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Bowler: Ravi Shah", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("3.2 ov  ·  24 runs  ·  2 wkts  ·  Econ 7.2", style = MaterialTheme.typography.bodySmall, color = PrimaryPlum)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("RECENT OVER:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("4", "1", "W", "0", "6", "1").forEach { ball ->
                                    Surface(
                                        shape = CircleShape,
                                        color = when (ball) {
                                            "W" -> MaterialTheme.colorScheme.error
                                            "4", "6" -> PrimaryPlum
                                            else -> MaterialTheme.colorScheme.surface
                                        },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(ball, style = MaterialTheme.typography.labelSmall, color = if (ball == "W" || ball == "4" || ball == "6") Color.White else MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Key Performers", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("${m.teamA} (${m.scoreA})  ·  ${m.teamB} (${m.scoreB})", style = MaterialTheme.typography.bodySmall, color = PrimaryPlum)
                            }
                        }
                    }
                }
            }
        }

        // Scoreboard Controls for Authorized Scorer Only when match is LIVE
        if (canControl && m.status == "LIVE") {
            item {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("LIVE SCORER CONTROLS", style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)
                            Text("Auto-syncs live to spectators", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // Team A Controls
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Score for ${m.teamA}:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PressButton(
                                    onClick = {
                                        scope.launch {
                                            val newScore = m.scoreA + 1
                                            val txt = if (m.sport == "Cricket") "$newScore/2 (ov)" else "$newScore - ${m.scoreB}"
                                            venueRepo.updateMatchScore(currentUserId, matchId, newScore, m.scoreB, txt, "${m.teamA} +1", currentUserEmail)
                                            onScoreUpdated()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("+1")
                                }
                                if (m.sport == "Cricket") {
                                    PressButton(
                                        onClick = {
                                            scope.launch {
                                                val newScore = m.scoreA + 4
                                                val txt = "$newScore/2 (ov)"
                                                venueRepo.updateMatchScore(currentUserId, matchId, newScore, m.scoreB, txt, "${m.teamA} hit FOUR (+4)", currentUserEmail)
                                                onScoreUpdated()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = PlayZonePrimaryCoral, contentColor = Color.White),
                                    ) {
                                        Text("+4")
                                    }
                                    PressButton(
                                        onClick = {
                                            scope.launch {
                                                val newScore = m.scoreA + 6
                                                val txt = "$newScore/2 (ov)"
                                                venueRepo.updateMatchScore(currentUserId, matchId, newScore, m.scoreB, txt, "${m.teamA} hit SIX (+6)", currentUserEmail)
                                                onScoreUpdated()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = PlayZoneSuccessGreen, contentColor = Color.White),
                                    ) {
                                        Text("+6")
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            if (m.scoreA > 0) {
                                                val newScore = m.scoreA - 1
                                                val txt = if (m.sport == "Cricket") "$newScore/2 (ov)" else "$newScore - ${m.scoreB}"
                                                venueRepo.updateMatchScore(currentUserId, matchId, newScore, m.scoreB, txt, "${m.teamA} score corrected (-1)", currentUserEmail)
                                                onScoreUpdated()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(0.9f),
                                ) {
                                    Text("-1")
                                }
                            }
                        }

                        // Team B Controls
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Score for ${m.teamB}:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PressButton(
                                    onClick = {
                                        scope.launch {
                                            val newScore = m.scoreB + 1
                                            val txt = if (m.sport == "Cricket") "${m.scoreA}/2 vs $newScore/3" else "${m.scoreA} - $newScore"
                                            venueRepo.updateMatchScore(currentUserId, matchId, m.scoreA, newScore, txt, "${m.teamB} +1", currentUserEmail)
                                            onScoreUpdated()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("+1")
                                }
                                if (m.sport == "Cricket") {
                                    PressButton(
                                        onClick = {
                                            scope.launch {
                                                val newScore = m.scoreB + 4
                                                val txt = "${m.scoreA}/2 vs $newScore/3"
                                                venueRepo.updateMatchScore(currentUserId, matchId, m.scoreA, newScore, txt, "${m.teamB} hit FOUR (+4)", currentUserEmail)
                                                onScoreUpdated()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = PlayZonePrimaryCoral, contentColor = Color.White),
                                    ) {
                                        Text("+4")
                                    }
                                    PressButton(
                                        onClick = {
                                            scope.launch {
                                                val newScore = m.scoreB + 6
                                                val txt = "${m.scoreA}/2 vs $newScore/3"
                                                venueRepo.updateMatchScore(currentUserId, matchId, m.scoreA, newScore, txt, "${m.teamB} hit SIX (+6)", currentUserEmail)
                                                onScoreUpdated()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = PlayZoneSuccessGreen, contentColor = Color.White),
                                    ) {
                                        Text("+6")
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            if (m.scoreB > 0) {
                                                val newScore = m.scoreB - 1
                                                val txt = if (m.sport == "Cricket") "${m.scoreA}/2 vs $newScore/3" else "${m.scoreA} - $newScore"
                                                venueRepo.updateMatchScore(currentUserId, matchId, m.scoreA, newScore, txt, "${m.teamB} score corrected (-1)", currentUserEmail)
                                                onScoreUpdated()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(0.9f),
                                ) {
                                    Text("-1")
                                }
                            }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 4.dp))

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    venueRepo.finishMatch(currentUserId, matchId, currentUserEmail)
                                    onScoreUpdated()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PlayZoneSuccessGreen),
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Conclude Match & Finalize Scorecard", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Text("MATCH EVENTS TIMELINE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }

        items(events, key = { it.id }) { event ->
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(event.timestampText, style = MaterialTheme.typography.labelLarge, color = PrimaryPlum)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(event.eventType, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(event.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

// --------------------------------------------------
// VENUE MANAGER EXPERIENCE SCREENS
// --------------------------------------------------

@Composable
private fun ManagerVenuesScreen(
    venueRepo: VenueBookingRepository,
    managerId: String,
    managerEmail: String = "",
    onAddVenue: () -> Unit,
    onAddSessionForVenue: (String) -> Unit,
    onAddEquipmentForVenue: (String) -> Unit,
    onEditVenue: (VenueEntity) -> Unit,
    onDeleteVenue: (String) -> Unit,
    onSelectSession: (SessionEntity) -> Unit,
    onEditSession: (SessionEntity) -> Unit,
    onDeleteSession: (String) -> Unit,
) {
    val venues by venueRepo.observeManagerVenues(managerId, managerEmail).collectAsStateWithLifecycle(emptyList())
    val sessions by venueRepo.observeManagerSessions(managerId, managerEmail).collectAsStateWithLifecycle(emptyList())
    var selectedTab by remember { mutableStateOf(0) } // 0: My Arenas, 1: Session Slots

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column {
                Text("ARENA OPERATIONS & INVENTORY", style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)
                Text("Manage Venues & Slots", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PressButton(
                    onClick = onAddVenue,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PlayZonePrimaryCoral, contentColor = Color.White),
                ) {
                    Icon(Icons.Rounded.AddBusiness, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add New Arena", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { onAddSessionForVenue("") },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.AddAlarm, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Publish Slot", fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("My Arenas (${venues.size})", fontWeight = FontWeight.Bold) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("All Session Slots (${sessions.size})", fontWeight = FontWeight.Bold) },
                )
            }
        }

        if (selectedTab == 0) {
            if (venues.isEmpty()) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(shape = CircleShape, color = PlayZonePrimaryCoral.copy(alpha = 0.15f), modifier = Modifier.size(54.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.AddBusiness, contentDescription = null, tint = PlayZonePrimaryCoral, modifier = Modifier.size(28.dp))
                                }
                            }
                            Text("No Arenas Registered", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Register your first sports venue or turf arena to publish bookable time slots for players.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            PressButton(
                                onClick = onAddVenue,
                                colors = ButtonDefaults.buttonColors(containerColor = PlayZonePrimaryCoral, contentColor = Color.White),
                            ) {
                                Text("+ Register New Arena", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            items(venues, key = { it.id }) { venue ->
                val venueSessions = sessions.filter { it.venueId == venue.id }
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Surface(shape = CircleShape, color = PlayZonePrimaryCoral.copy(alpha = 0.15f)) {
                                    Text(venue.venueType.uppercase(), Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(venue.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("${venue.address}, ${venue.city}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Sports: ${venue.supportedSports}", style = MaterialTheme.typography.bodySmall, color = PlayZoneDeepNavy, fontWeight = FontWeight.SemiBold)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { onEditVenue(venue) }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit Arena", tint = PlayZonePrimaryCoral)
                                }
                                IconButton(onClick = { onDeleteVenue(venue.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete Arena", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PressButton(
                                onClick = { onAddSessionForVenue(venue.id) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = PlayZonePrimaryCoral, contentColor = Color.White),
                            ) {
                                Icon(Icons.Rounded.AddAlarm, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("+ Add Slot", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { onAddEquipmentForVenue(venue.id) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.Sports, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("+ Equipment", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Preview of slots for this arena
                        if (venueSessions.isNotEmpty()) {
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            Text("PUBLISHED SLOTS (${venueSessions.size})", style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)
                            venueSessions.take(5).forEach { slot ->
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth().clickable { onSelectSession(slot) },
                                ) {
                                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("${slot.date} · ${slot.startTime}–${slot.endTime} (${slot.sport})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                            Text("₹${slot.price.toInt()} · ${slot.bookedCount}/${slot.capacity} Booked", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Surface(
                                            shape = CircleShape,
                                            color = if (slot.status == "AVAILABLE") PlayZoneSuccessGreen else PlayZonePrimaryCoral,
                                        ) {
                                            Text(slot.status, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        IconButton(onClick = { onEditSession(slot) }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Rounded.Edit, contentDescription = "Edit Slot", modifier = Modifier.size(16.dp), tint = PlayZonePrimaryCoral)
                                        }
                                        IconButton(onClick = { onDeleteSession(slot.id) }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete Slot", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Tab 1: All Session Slots
            if (sessions.isEmpty()) {
                item {
                    EmptyCard("No Session Slots", "Publish open slots for your arenas so players can book games.")
                }
            }

            items(sessions, key = { it.id }) { session ->
                val venue = venues.find { it.id == session.venueId }
                SessionCard(
                    session = session,
                    venueName = venue?.name,
                    venueLocation = venue?.let { "${it.address}, ${it.area}" },
                    onClick = { onSelectSession(session) },
                    onEdit = { onEditSession(session) },
                    onDelete = { onDeleteSession(session.id) },
                )
            }
        }
    }
}

@Composable
private fun ManagerDashboardScreen(
    venueRepo: VenueBookingRepository,
    managerId: String,
    managerEmail: String = "",
    onOpenRequests: () -> Unit,
    onAddSession: () -> Unit,
    onAddVenue: () -> Unit,
    onAddEquipment: () -> Unit,
    onSelectSession: (SessionEntity) -> Unit,
    onEditSession: (SessionEntity) -> Unit,
    onEditVenue: (VenueEntity) -> Unit,
    onDeleteVenue: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
) {
    val venues by venueRepo.observeManagerVenues(managerId, managerEmail).collectAsStateWithLifecycle(emptyList())
    val bookings by venueRepo.observeManagerBookings(managerId, managerEmail).collectAsStateWithLifecycle(emptyList())
    val sessions by venueRepo.observeManagerSessions(managerId, managerEmail).collectAsStateWithLifecycle(emptyList())
    val pendingRequests = bookings.count { it.status == "PENDING" }
    val confirmedCount = bookings.count { it.status == "CONFIRMED" }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column {
                Text("VENUE OPERATING SYSTEM", style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)
                Text("Manager Dashboard", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            }
        }

        // Hero Command Center Card
        item {
            Surface(shape = MaterialTheme.shapes.large, color = PlayZoneDeepNavy, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(160.dp)) {
                    Image(
                        painterResource(R.drawable.venue_cricket),
                        contentDescription = "Arena Management",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(
                                listOf(PlayZoneDeepNavy.copy(alpha = 0.95f), PlayZoneDeepNavy.copy(alpha = 0.65f), Color.Transparent),
                            ),
                        ),
                    )
                    Column(
                        Modifier.align(Alignment.CenterStart).padding(20.dp).fillMaxHeight(),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("Arena Operations", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Publish slots, approve requests & manage inventory.", style = MaterialTheme.typography.bodyMedium, color = PlayZoneSoftCream)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PressButton(onClick = onAddSession, colors = ButtonDefaults.buttonColors(containerColor = PlayZonePrimaryCoral, contentColor = Color.White)) {
                                Icon(Icons.Rounded.AddAlarm, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Slot", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            PressButton(onClick = onAddVenue, colors = ButtonDefaults.buttonColors(containerColor = PlayZoneSecondaryNavy, contentColor = Color.White)) {
                                Icon(Icons.Rounded.AddBusiness, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Arena", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("${venues.size}", "ARENAS", Modifier.weight(1f))
                StatTile("${sessions.size}", "SLOTS", Modifier.weight(1f))
                StatTile("$pendingRequests", "PENDING", Modifier.weight(1f))
                StatTile("$confirmedCount", "CONFIRMED", Modifier.weight(1f))
            }
        }

        if (pendingRequests > 0) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = PlayZonePrimaryCoral.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PlayZonePrimaryCoral),
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenRequests),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.NotificationImportant, contentDescription = null, tint = PlayZonePrimaryCoral)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("ACTION REQUIRED", style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)
                            Text("$pendingRequests booking requests waiting for approval.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = PlayZonePrimaryCoral)
                    }
                }
            }
        }

        item {
            Text("QUICK ACTIONS", style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PressButton(onClick = onAddSession, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = PlayZonePrimaryCoral, contentColor = Color.White)) {
                        Icon(Icons.Rounded.AddAlarm, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Session")
                    }
                    OutlinedButton(onClick = onAddVenue, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.AddBusiness, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Venue")
                    }
                }
                OutlinedButton(onClick = onAddEquipment, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Sports, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Sport Equipment")
                }
            }
        }

        if (sessions.isNotEmpty()) {
            item {
                Text("MANAGED SESSION SLOTS (${sessions.size})", style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)
            }
            items(sessions, key = { it.id }) { session ->
                val venue = venues.find { it.id == session.venueId }
                SessionCard(
                    session = session,
                    venueName = venue?.name,
                    venueLocation = venue?.let { "${it.address}, ${it.area}" },
                    onClick = { onSelectSession(session) },
                    onEdit = { onEditSession(session) },
                    onDelete = { onDeleteSession(session.id) },
                )
            }
        }

        item {
            Text("MANAGED VENUES", style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)
        }

        items(venues, key = { it.id }) { venue ->
            Box {
                VenueCard(venue = venue, onClick = { onEditVenue(venue) })
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    IconButton(
                        onClick = { onEditVenue(venue) },
                    ) {
                        Surface(shape = CircleShape, color = PlayZonePrimaryCoral) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit Venue", tint = Color.White, modifier = Modifier.padding(6.dp).size(16.dp))
                        }
                    }
                    IconButton(
                        onClick = { onDeleteVenue(venue.id) },
                    ) {
                        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.65f)) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete Venue", tint = Color.White, modifier = Modifier.padding(6.dp).size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleScreen(
    venueRepo: VenueBookingRepository,
    managerId: String,
    managerEmail: String = "",
    onSelectSession: (SessionEntity) -> Unit,
    onEditSession: ((SessionEntity) -> Unit)? = null,
    onOpenScoreboard: ((String) -> Unit)? = null,
    onCancelBooking: ((String) -> Unit)? = null,
    onDeleteSession: ((String) -> Unit)? = null,
) {
    val venues by venueRepo.observeManagerVenues(managerId, managerEmail).collectAsStateWithLifecycle(emptyList())
    val bookings by venueRepo.observeManagerBookings(managerId, managerEmail).collectAsStateWithLifecycle(emptyList())
    val sessions by venueRepo.observeManagerSessions(managerId, managerEmail).collectAsStateWithLifecycle(emptyList())

    val confirmedBookings = remember(bookings) { bookings.filter { it.status == "CONFIRMED" } }
    val cancelledBookings = remember(bookings) { bookings.filter { it.status == "CANCELLED" || it.status == "REJECTED" } }
    var scheduleFilter by remember { mutableStateOf("CONFIRMED") } // CONFIRMED, SLOTS, CANCELLED

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column {
                Text("OPERATIONAL SCHEDULE & OCCUPANCY", style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)
                Text("Arena Schedule", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = scheduleFilter == "CONFIRMED",
                    onClick = { scheduleFilter = "CONFIRMED" },
                    label = { Text("Confirmed Matches (${confirmedBookings.size})") },
                )
                FilterChip(
                    selected = scheduleFilter == "SLOTS",
                    onClick = { scheduleFilter = "SLOTS" },
                    label = { Text("Published Slots (${sessions.size})") },
                )
                FilterChip(
                    selected = scheduleFilter == "CANCELLED",
                    onClick = { scheduleFilter = "CANCELLED" },
                    label = { Text("Cancelled / Past (${cancelledBookings.size})") },
                )
            }
        }

        when (scheduleFilter) {
            "CONFIRMED" -> {
                if (confirmedBookings.isEmpty()) {
                    item {
                        EmptyCard(
                            "No Confirmed Bookings",
                            "When player booking requests are approved, they will appear here with active session schedules."
                        )
                    }
                }
                items(confirmedBookings, key = { it.id }) { booking ->
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(booking.sport.uppercase(), style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)
                                Surface(shape = CircleShape, color = PlayZoneSuccessGreen) {
                                    Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("CONFIRMED", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Text("${booking.date}  ·  ${booking.startTime}–${booking.endTime}", style = MaterialTheme.typography.titleMedium, color = PlayZoneDeepNavy, fontWeight = FontWeight.Bold)
                            Text(booking.venueName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Booked Player: ${booking.playerName}  ·  ${booking.playerPhone.ifBlank { booking.playerEmail }}", style = MaterialTheme.typography.bodyMedium)
                            Text("Ref: #${booking.bookingReference}  ·  Players: ${booking.playerCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PressButton(
                                    onClick = { onOpenScoreboard?.invoke("MATCH-${booking.id}") },
                                    colors = ButtonDefaults.buttonColors(containerColor = PlayZonePrimaryCoral, contentColor = Color.White),
                                ) {
                                    Icon(Icons.Rounded.SportsScore, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Open Scorecard", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { onCancelBooking?.invoke(booking.id) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                ) {
                                    Text("Cancel Booking")
                                }
                            }
                        }
                    }
                }
            }
            "SLOTS" -> {
                if (sessions.isEmpty()) {
                    item {
                        EmptyCard("No Session Slots", "Publish slots from the Venues tab to accept bookings.")
                    }
                }
                items(sessions, key = { it.id }) { session ->
                    val venue = venues.find { it.id == session.venueId }
                    SessionCard(
                        session = session,
                        venueName = venue?.name,
                        venueLocation = venue?.let { "${it.address}, ${it.area}" },
                        onClick = { onSelectSession(session) },
                        onEdit = onEditSession?.let { { it(session) } },
                        onDelete = onDeleteSession?.let { { it(session.id) } },
                    )
                }
            }
            "CANCELLED" -> {
                if (cancelledBookings.isEmpty()) {
                    item {
                        EmptyCard("No Cancelled Bookings", "Cancelled or declined bookings will be archived here.")
                    }
                }
                items(cancelledBookings, key = { it.id }) { booking ->
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(booking.sport.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Surface(shape = CircleShape, color = if (booking.status == "REJECTED") MaterialTheme.colorScheme.error else Color.Gray) {
                                    Text(booking.status, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text("${booking.date}  ·  ${booking.startTime}–${booking.endTime}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(booking.venueName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Player: ${booking.playerName}  ·  Ref: #${booking.bookingReference}", style = MaterialTheme.typography.bodySmall)

                            val reason = booking.cancellationReason.ifBlank { booking.rejectionReason }
                            if (reason.isNotBlank()) {
                                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                                    Text("Reason: $reason", Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingRequestsScreen(
    venueRepo: VenueBookingRepository,
    managerId: String,
    managerEmail: String = "",
    onApprove: (String) -> Unit,
    onReject: (String, String) -> Unit,
) {
    val bookings by venueRepo.observeManagerBookings(managerId, managerEmail).collectAsStateWithLifecycle(emptyList())
    val pending = bookings.filter { it.status == "PENDING" }
    val confirmed = bookings.filter { it.status == "CONFIRMED" }

    var rejectingBookingId by remember { mutableStateOf<String?>(null) }
    var rejectionReason by remember { mutableStateOf("Time slot unavailable") }
    var customRejectionNote by remember { mutableStateOf("") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Booking Requests", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }

        if (pending.isEmpty()) {
            item { EmptyCard("No pending requests", "New player booking requests will appear here for review.") }
        }

        items(pending, key = { it.id }) { request ->
            val reqStart = remember(request.startTime) { venueRepo.parseTimeToMinutes(request.startTime) }
            val reqEnd = remember(request.endTime) { venueRepo.parseTimeToMinutes(request.endTime) }
            val conflictingBooking = remember(confirmed, request) {
                confirmed.firstOrNull { c ->
                    c.venueId == request.venueId && c.date == request.date &&
                    venueRepo.isTimeOverlapping(reqStart, reqEnd, venueRepo.parseTimeToMinutes(c.startTime), venueRepo.parseTimeToMinutes(c.endTime))
                }
            }

            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("REQUEST #${request.bookingReference}", style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)
                        Text(request.sport.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(request.playerName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Venue: ${request.venueName}", style = MaterialTheme.typography.bodyMedium)
                    Text("Requested Slot: ${request.date} (${request.startTime}–${request.endTime})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("Phone: ${request.playerPhone}  ·  Email: ${request.playerEmail}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (request.equipmentRequested.isNotBlank()) {
                        Text("Equipment: ${request.equipmentRequested}", style = MaterialTheme.typography.bodySmall, color = PlayZoneDeepNavy)
                    }

                    // Schedule Conflict Indicator
                    if (conflictingBooking != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = PlayZoneErrorRed.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PlayZoneErrorRed),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = PlayZoneErrorRed, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Schedule Conflict: Overlaps with confirmed booking by ${conflictingBooking.playerName} (${conflictingBooking.startTime}–${conflictingBooking.endTime})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = PlayZoneSuccessGreen.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CheckCircleOutline, contentDescription = null, tint = PlayZoneSuccessGreen, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("No schedule conflict · Slot is clear for confirmation", style = MaterialTheme.typography.labelSmall, color = PlayZoneSuccessGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PressButton(
                            onClick = { onApprove(request.id) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = PlayZonePrimaryCoral, contentColor = Color.White),
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Approve")
                        }
                        OutlinedButton(onClick = { rejectingBookingId = request.id }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Close, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Reject")
                        }
                    }
                }
            }
        }
    }

    if (rejectingBookingId != null) {
        AlertDialog(
            onDismissRequest = { rejectingBookingId = null },
            title = { Text("Reject Booking Request") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select reason for rejection:")
                    listOf("Time unavailable", "Venue maintenance", "Already booked", "Insufficient capacity", "Other").forEach { reason ->
                        FilterChip(
                            selected = rejectionReason == reason,
                            onClick = { rejectionReason = reason },
                            label = { Text(reason) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = customRejectionNote,
                        onValueChange = { customRejectionNote = it },
                        label = { Text("Additional note to player (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val finalReason = if (customRejectionNote.isNotBlank()) "$rejectionReason: $customRejectionNote" else rejectionReason
                    onReject(rejectingBookingId!!, finalReason)
                    rejectingBookingId = null
                }) {
                    Text("Confirm Rejection", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectingBookingId = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun NotificationsScreen(venueRepo: VenueBookingRepository, userId: String, userEmail: String = "") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notifications by venueRepo.observeNotifications(userId, userEmail).collectAsStateWithLifecycle(emptyList())

    LaunchedEffect(userId, userEmail) {
        venueRepo.markNotificationsRead(userId, userEmail)
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("ACTIVITY & ALERTS", style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)
                    Text("Notifications (${notifications.size})", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                }
                if (notifications.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                venueRepo.clearAllNotifications(userId, userEmail)
                                SystemNotificationHelper.cancelAllNotifications(context)
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear All", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (notifications.isEmpty()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(shape = CircleShape, color = PlayZonePrimaryCoral.copy(alpha = 0.15f), modifier = Modifier.size(54.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Notifications, contentDescription = null, tint = PlayZonePrimaryCoral, modifier = Modifier.size(28.dp))
                            }
                        }
                        Text("All Caught Up!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "You have cleared all alerts. Booking approvals, session updates, and match scoreboard notices will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        items(notifications, key = { it.id }) { notif ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Surface(
                        shape = CircleShape,
                        color = when (notif.type) {
                            "BOOKING_CONFIRMED" -> PlayZoneSuccessGreen
                            "CONFLICT", "BOOKING_REJECTED" -> MaterialTheme.colorScheme.error
                            "SESSION_CANCELLED" -> PlayZonePrimaryCoral
                            else -> PlayZoneSecondaryNavy
                        },
                        modifier = Modifier.size(38.dp).padding(top = 2.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                when (notif.type) {
                                    "BOOKING_CONFIRMED" -> Icons.Rounded.CheckCircle
                                    "CONFLICT", "BOOKING_REJECTED" -> Icons.Rounded.Close
                                    else -> Icons.Rounded.Notifications
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                notif.type.replace('_', ' '),
                                style = MaterialTheme.typography.labelSmall,
                                color = when (notif.type) {
                                    "BOOKING_CONFIRMED" -> PlayZoneSuccessGreen
                                    "CONFLICT", "BOOKING_REJECTED" -> MaterialTheme.colorScheme.error
                                    else -> PlayZonePrimaryCoral
                                },
                                fontWeight = FontWeight.Bold,
                            )
                            if (!notif.isRead) {
                                Spacer(Modifier.width(6.dp))
                                Surface(shape = CircleShape, color = PlayZonePrimaryCoral, modifier = Modifier.size(6.dp)) {}
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(notif.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        Text(notif.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                venueRepo.deleteNotification(notif.id)
                                SystemNotificationHelper.cancelNotification(context, (notif.title + notif.message).hashCode())
                            }
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(
    user: UserEntity?,
    role: String,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSignOut: () -> Unit,
    onEditProfile: (() -> Unit)? = null,
) {
    val context = LocalContext.current

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Surface(shape = MaterialTheme.shapes.large, color = PrimaryPlum, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = CircleShape, color = PeachAccent, modifier = Modifier.size(64.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Person, contentDescription = null, tint = PrimaryPlum, modifier = Modifier.size(36.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(user?.name ?: "User Profile", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    user?.email?.takeIf { it.isNotBlank() }?.let { email ->
                        Text(email, style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
                    }
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.2f)) {
                        Text(role.replace('_', ' '), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = PeachAccent)
                    }
                    if (onEditProfile != null) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onEditProfile,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Edit Name & Profile Details", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        item {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (isDarkTheme) Icons.Rounded.DarkMode else Icons.Rounded.LightMode, contentDescription = null, tint = PrimaryPlum)
                    Text("Dark Theme", Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleMedium)
                    Switch(checked = isDarkTheme, onCheckedChange = { onToggleTheme() })
                }
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    runCatching {
                        shareStandingsPdf(
                            context = context,
                            tournamentName = "PS 22 Premier Sports League",
                            rows = listOf(
                                StandingRow(
                                    rank = 1,
                                    team = TeamEntity("T1", "TRN1", "Harbor City Strikers", "HCS", "#FF542A52", 1),
                                    played = 6,
                                    wins = 5,
                                    draws = 1,
                                    losses = 0,
                                    goalsFor = 14,
                                    goalsAgainst = 4,
                                    points = 16,
                                ),
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = PrimaryPlum)
                Spacer(Modifier.width(8.dp))
                Text("Export League Standings PDF")
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    runCatching {
                        shareFixturesPdf(
                            context = context,
                            tournamentName = "PS 22 Premier Sports League",
                            fixtures = listOf(
                                FixtureEntity(
                                    id = "F1",
                                    tournamentId = "TRN1",
                                    round = "Round 1",
                                    homeTeamId = "T1",
                                    awayTeamId = "T2",
                                    kickoffAt = System.currentTimeMillis(),
                                    venue = "Premier Turf",
                                    status = "COMPLETED",
                                    homeScore = 2,
                                    awayScore = 1,
                                ),
                            ),
                            teams = listOf(
                                TeamEntity("T1", "TRN1", "Harbor City Strikers", "HCS", "#FF542A52", 1),
                                TeamEntity("T2", "TRN1", "Metro United", "MUN", "#FF1976D2", 2),
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Rounded.DateRange, contentDescription = null, tint = PrimaryPlum)
                Spacer(Modifier.width(8.dp))
                Text("Export Fixtures Schedule PDF")
            }
        }

        item {
            PressButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = Color.White),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sign out", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// --------------------------------------------------
// HELPER COMPONENTS & DIALOGS
// --------------------------------------------------

@Composable
private fun AppHeader(
    user: UserEntity?,
    role: String,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSignOut: () -> Unit,
    onEditProfile: (() -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black,
                border = androidx.compose.foundation.BorderStroke(1.dp, PlayZoneChampagneGold.copy(alpha = 0.4f)),
                modifier = Modifier.size(44.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.playzone_logo),
                    contentDescription = "PlayZone Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("PlayZone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = if (onEditProfile != null) Modifier.clickable { onEditProfile() } else Modifier,
                ) {
                    Text(
                        user?.name ?: "User",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (onEditProfile != null) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = "Edit Profile Name",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PlayZoneBrandPurple.copy(alpha = 0.16f),
                    ) {
                        Text(
                            role.replace('_', ' '),
                            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = PlayZoneBrandPurple,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Visible High-Contrast Dark/Light Mode Button
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.size(38.dp),
            ) {
                IconButton(onClick = onToggleTheme, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        contentDescription = "Toggle theme",
                        tint = PlayZoneChampagneGold,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))

            // Visible High-Contrast Sign Out Button
            Surface(
                shape = CircleShape,
                color = PlayZoneErrorRed.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, PlayZoneErrorRed.copy(alpha = 0.35f)),
                modifier = Modifier.size(38.dp),
            ) {
                IconButton(onClick = onSignOut, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = "Sign out",
                        tint = PlayZoneErrorRed,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PressButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(containerColor = PrimaryPlum, contentColor = Color.White),
    content: @Composable RowScope.() -> Unit,
) {
    val context = LocalContext.current
    val isReduceMotion = remember {
        val scale = runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
        scale == 0f
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !isReduceMotion) 0.985f else 1f, animationSpec = tween(100), label = "btn-scale")

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = MaterialTheme.shapes.medium,
        colors = colors,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        content = content,
    )
}

@Composable
private fun PressCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    shape: RoundedCornerShape = MaterialTheme.shapes.medium as RoundedCornerShape,
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val isReduceMotion = remember {
        val scale = runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
        scale == 0f
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !isReduceMotion) 0.988f else 1f, animationSpec = tween(100), label = "card-scale")

    Surface(
        shape = shape,
        color = containerColor,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clickable(interactionSource = interaction, indication = ripple(), onClick = onClick),
    ) {
        Column { content() }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.headlineLarge, color = PlayZoneChampagneGold, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyCard(title: String, body: String) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = PlayZoneBrandPurple.copy(alpha = 0.15f),
                modifier = Modifier.size(54.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.SportsTennis, contentDescription = null, tint = PlayZoneBrandPurple, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun AddVenueDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Sports Turf") }
    var sports by remember { mutableStateOf("Football, Cricket") }
    var addr by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("+91 ") }
    var customCoverUri by remember { mutableStateOf<String?>(null) }
    var selectedDefaultSport by remember { mutableStateOf("turf_football") }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            customCoverUri = uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Sports Venue") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Venue Name") }, singleLine = true)
                OutlinedTextField(type, { type = it }, label = { Text("Venue Type") }, singleLine = true)
                OutlinedTextField(sports, { sports = it }, label = { Text("Supported Sports") }, singleLine = true)
                OutlinedTextField(addr, { addr = it }, label = { Text("Address") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Contact Phone") }, singleLine = true)

                Spacer(Modifier.height(4.dp))
                Text("Venue Cover Media Image:", style = MaterialTheme.typography.labelSmall, color = PrimaryPlum, fontWeight = FontWeight.Bold)

                if (customCoverUri != null) {
                    Box(Modifier.fillMaxWidth().height(120.dp)) {
                        VenueCardImage(coverImage = customCoverUri!!, sport = sports, modifier = Modifier.fillMaxSize())
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clickable { customCoverUri = null },
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove Custom Media", tint = Color.White, modifier = Modifier.padding(4.dp).size(16.dp))
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { galleryPicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add Venue Image / Media from Device")
                    }
                    Text("Or use default sport court visual:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val defaultVisuals = listOf(
                            "venue_football" to "Football Turf",
                            "venue_cricket" to "Cricket Ground",
                            "venue_badminton" to "Badminton Court",
                            "venue_basketball" to "Basketball Arena",
                            "venue_tennis" to "Tennis Court",
                            "venue_volleyball" to "Volleyball Court",
                            "venue_turf" to "Multi-Sport Turf",
                        )
                        items(defaultVisuals) { (imgKey, imgLabel) ->
                            FilterChip(
                                selected = selectedDefaultSport == imgKey,
                                onClick = {
                                    selectedDefaultSport = imgKey
                                    sports = imgLabel.replace(" Court", "").replace(" Ground", "").replace(" Arena", "").replace(" Multi-Sport ", "")
                                },
                                label = { Text(imgLabel) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalCover = customCoverUri ?: selectedDefaultSport
                    onSave(name, type, sports, addr, phone, finalCover)
                },
                enabled = name.isNotBlank(),
            ) {
                Text("Save Venue")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddEquipmentDialog(
    venueRepo: VenueBookingRepository,
    managerId: String,
    initialVenueId: String? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int, Boolean, Double) -> Unit,
) {
    val venues by venueRepo.observeManagerVenues(managerId).collectAsStateWithLifecycle(emptyList())
    var selectedVenueId by remember { mutableStateOf(initialVenueId ?: "") }
    var sport by remember { mutableStateOf("Football") }
    var name by remember { mutableStateOf("Match Footballs") }
    var quantityStr by remember { mutableStateOf("6") }
    var isFree by remember { mutableStateOf(true) }
    var chargeStr by remember { mutableStateOf("0") }

    LaunchedEffect(venues) {
        if (venues.isNotEmpty() && selectedVenueId.isBlank()) {
            selectedVenueId = initialVenueId?.takeIf { id -> venues.any { it.id == id } } ?: venues.first().id
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Sport Equipment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select Venue:", style = MaterialTheme.typography.labelSmall)
                venues.forEach { v ->
                    FilterChip(selected = selectedVenueId == v.id, onClick = { selectedVenueId = v.id }, label = { Text(v.name) })
                }
                OutlinedTextField(sport, { sport = it }, label = { Text("Sport (e.g. Cricket, Football)") }, singleLine = true)
                OutlinedTextField(name, { name = it }, label = { Text("Equipment Name (e.g. Kashmir Willow Bat)") }, singleLine = true)
                OutlinedTextField(quantityStr, { quantityStr = it }, label = { Text("Quantity Available") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isFree, onCheckedChange = { isFree = it })
                    Text("Included Free with Session", style = MaterialTheme.typography.bodyMedium)
                }
                if (!isFree) {
                    OutlinedTextField(chargeStr, { chargeStr = it }, label = { Text("Additional Charge (₹)") }, singleLine = true)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(selectedVenueId, sport, name, quantityStr.toIntOrNull() ?: 5, isFree, chargeStr.toDoubleOrNull() ?: 0.0) },
                enabled = selectedVenueId.isNotBlank() && name.isNotBlank(),
            ) {
                Text("Add Equipment")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddSessionDialog(
    venueRepo: VenueBookingRepository,
    managerId: String,
    managerEmail: String = "",
    initialVenueId: String? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, Int, Double, String) -> Unit,
) {
    val venues by venueRepo.observeManagerVenues(managerId, managerEmail).collectAsStateWithLifecycle(emptyList())
    var selectedVenueId by remember { mutableStateOf(initialVenueId ?: "") }
    var sport by remember { mutableStateOf("Football") }
    var date by remember { mutableStateOf(venueRepo.getIndianStandardToday()) }
    var start by remember { mutableStateOf("19:00") }
    var end by remember { mutableStateOf("20:00") }
    var capacityStr by remember { mutableStateOf("22") }
    var priceStr by remember { mutableStateOf("800") }
    var customCoverUri by remember { mutableStateOf<String?>(null) }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            customCoverUri = uri.toString()
        }
    }

    LaunchedEffect(venues) {
        if (venues.isNotEmpty() && selectedVenueId.isBlank()) {
            selectedVenueId = initialVenueId?.takeIf { id -> venues.any { it.id == id } } ?: venues.first().id
            val matchedVenue = venues.find { it.id == selectedVenueId } ?: venues.first()
            val firstSport = matchedVenue.supportedSports.split(",").firstOrNull()?.trim() ?: "Football"
            sport = firstSport
            capacityStr = venueRepo.getSportDefaultPlayerCount(firstSport).toString()
        }
    }

    LaunchedEffect(sport) {
        if (sport.isNotBlank()) {
            capacityStr = venueRepo.getSportDefaultPlayerCount(sport).toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Session Slot", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select Venue:", style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(venues, key = { it.id }) { v ->
                        FilterChip(
                            selected = selectedVenueId == v.id,
                            onClick = {
                                selectedVenueId = v.id
                                val s = v.supportedSports.split(",").firstOrNull()?.trim() ?: "Football"
                                sport = s
                            },
                            label = { Text(v.name) },
                        )
                    }
                }
                OutlinedTextField(sport, { sport = it }, label = { Text("Sport") }, singleLine = true)
                OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true)

                Text("Quick Slot Presets:", style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val presets = listOf(
                        "06:00" to "07:00",
                        "07:00" to "08:00",
                        "16:00" to "17:00",
                        "17:00" to "18:00",
                        "18:00" to "19:00",
                        "19:00" to "20:00",
                        "20:00" to "21:00",
                        "21:00" to "22:00",
                    )
                    items(presets) { (s, e) ->
                        FilterChip(
                            selected = start == s && end == e,
                            onClick = { start = s; end = e },
                            label = { Text("$s–$e") },
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(start, { start = it }, label = { Text("Start Time") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(end, { end = it }, label = { Text("End Time") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(capacityStr, { capacityStr = it }, label = { Text("Player Capacity") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(priceStr, { priceStr = it }, label = { Text("Price (₹)") }, modifier = Modifier.weight(1f), singleLine = true)
                }

                Spacer(Modifier.height(4.dp))
                Text("Session Media / Photo:", style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)

                if (customCoverUri != null) {
                    Box(Modifier.fillMaxWidth().height(120.dp)) {
                        VenueCardImage(coverImage = customCoverUri!!, sport = sport, modifier = Modifier.fillMaxSize())
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clickable { customCoverUri = null },
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove Custom Media", tint = Color.White, modifier = Modifier.padding(4.dp).size(16.dp))
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { galleryPicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add Session Media from Device")
                    }
                    Text(
                        "If no device image is selected, the vibrant sport photo for $sport will automatically be used.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cap = capacityStr.toIntOrNull() ?: venueRepo.getSportDefaultPlayerCount(sport)
                    val prc = priceStr.toDoubleOrNull() ?: 800.0
                    onSave(selectedVenueId, sport, date, start, end, cap, prc, customCoverUri ?: "")
                },
                enabled = selectedVenueId.isNotBlank(),
            ) {
                Text("Add Session")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditSessionDialog(
    session: SessionEntity,
    venueRepo: VenueBookingRepository,
    managerId: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Int, Double, String, String) -> Unit,
) {
    var sport by remember { mutableStateOf(session.sport) }
    var date by remember { mutableStateOf(session.date) }
    var start by remember { mutableStateOf(session.startTime) }
    var end by remember { mutableStateOf(session.endTime) }
    var capacityStr by remember { mutableStateOf(session.capacity.toString()) }
    var priceStr by remember { mutableStateOf(session.price.toInt().toString()) }
    var status by remember { mutableStateOf(session.status) }
    var customCoverUri by remember { mutableStateOf<String?>(session.coverImage.ifBlank { null }) }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            customCoverUri = uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Session Slot", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(sport, { sport = it }, label = { Text("Sport") }, singleLine = true)
                OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(start, { start = it }, label = { Text("Start Time") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(end, { end = it }, label = { Text("End Time") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(capacityStr, { capacityStr = it }, label = { Text("Player Capacity") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(priceStr, { priceStr = it }, label = { Text("Price (₹)") }, modifier = Modifier.weight(1f), singleLine = true)
                }

                Text("Status:", style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("AVAILABLE", "BOOKED", "MAINTENANCE").forEach { s ->
                        FilterChip(
                            selected = status == s,
                            onClick = { status = s },
                            label = { Text(s) },
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text("Location Image / Media:", style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)

                if (customCoverUri != null) {
                    Box(Modifier.fillMaxWidth().height(120.dp)) {
                        VenueCardImage(coverImage = customCoverUri!!, sport = sport, modifier = Modifier.fillMaxSize())
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clickable { customCoverUri = null },
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove Media", tint = Color.White, modifier = Modifier.padding(4.dp).size(16.dp))
                        }
                    }
                    TextButton(onClick = { galleryPicker.launch("image/*") }) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Change Image from Device")
                    }
                } else {
                    OutlinedButton(
                        onClick = { galleryPicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add Location Image from Device")
                    }
                    Text(
                        "If no device image is added, the location details will be prominently displayed on the session card with the sport visual.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cap = capacityStr.toIntOrNull() ?: venueRepo.getSportDefaultPlayerCount(sport)
                    val prc = priceStr.toDoubleOrNull() ?: 800.0
                    onSave(sport, date, start, end, cap, prc, customCoverUri ?: "", status)
                },
                enabled = sport.isNotBlank() && start.isNotBlank() && end.isNotBlank(),
            ) {
                Text("Update Session")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditVenueDialog(
    venue: VenueEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf(venue.name) }
    var type by remember { mutableStateOf(venue.venueType) }
    var sports by remember { mutableStateOf(venue.supportedSports) }
    var addr by remember { mutableStateOf(venue.address) }
    var phone by remember { mutableStateOf(venue.phone) }
    var customCoverUri by remember { mutableStateOf<String?>(venue.coverImage.ifBlank { null }) }
    var selectedDefaultSport by remember { mutableStateOf("venue_turf") }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            customCoverUri = uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Sports Venue", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Venue Name") }, singleLine = true)
                OutlinedTextField(type, { type = it }, label = { Text("Venue Type") }, singleLine = true)
                OutlinedTextField(sports, { sports = it }, label = { Text("Supported Sports") }, singleLine = true)
                OutlinedTextField(addr, { addr = it }, label = { Text("Address") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Contact Phone") }, singleLine = true)

                Spacer(Modifier.height(4.dp))
                Text("Venue Image / Media:", style = MaterialTheme.typography.labelSmall, color = PlayZonePrimaryCoral, fontWeight = FontWeight.Bold)

                if (customCoverUri != null) {
                    Box(Modifier.fillMaxWidth().height(120.dp)) {
                        VenueCardImage(coverImage = customCoverUri!!, sport = sports, modifier = Modifier.fillMaxSize())
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clickable { customCoverUri = null },
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove Venue Media", tint = Color.White, modifier = Modifier.padding(4.dp).size(16.dp))
                        }
                    }
                    TextButton(onClick = { galleryPicker.launch("image/*") }) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Change Venue Image from Device")
                    }
                } else {
                    OutlinedButton(
                        onClick = { galleryPicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add Venue Image from Device")
                    }
                    Text("Or choose a default sport arena style:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val defaultVisuals = listOf(
                            "venue_football" to "Football Turf",
                            "venue_cricket" to "Cricket Ground",
                            "venue_badminton" to "Badminton Court",
                            "venue_basketball" to "Basketball Arena",
                            "venue_tennis" to "Tennis Court",
                            "venue_volleyball" to "Volleyball Court",
                            "venue_turf" to "Multi-Sport Turf",
                        )
                        items(defaultVisuals) { (imgKey, imgLabel) ->
                            FilterChip(
                                selected = selectedDefaultSport == imgKey,
                                onClick = {
                                    selectedDefaultSport = imgKey
                                    sports = imgLabel.replace(" Court", "").replace(" Ground", "").replace(" Arena", "").replace(" Multi-Sport ", "")
                                },
                                label = { Text(imgLabel) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalCover = customCoverUri ?: selectedDefaultSport
                    onSave(name, type, sports, addr, phone, finalCover)
                },
                enabled = name.isNotBlank(),
            ) {
                Text("Update Venue")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
