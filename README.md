# PS 22 · Sports Venue Discovery, Booking & Management Platform

Complete, production-quality Android application connecting **VENUE MANAGERS** (who operate sports arenas, turfs, courts, manage equipment, operating hours, sessions, approve/reject booking requests, and manage live match scoreboards) and **PLAYERS** (who discover venues, filter sports sessions, book slots, track booking status, view live scoreboards, and review completed sessions).

Built with Jetpack Compose, Room database, Coroutines/Flow, WorkManager, and a custom design system anchored on **Primary Plum (`#542A52`)** and **Peach Accent (`#FFB39A`)**.

---

## Key Product Features

### 1. Authentication & Role Persistence
- **Google Sign-In**: Start with `"Continue with Google"`.
- **First-Time Role Selection**: Choose between **VENUE MANAGER** ("Manage arenas, sports sessions and bookings.") and **PLAYER** ("Discover arenas and book sports sessions.").
- **Persistent Role Authorization**: Stored against the authenticated user account in Room Database (`UserEntity`). Future sign-ins automatically route to the correct role experience without re-asking.

### 2. Player Experience
- **Home & Discovery**: Editorial greeting, search bar, popular sport category chips, nearby/featured venue hero cards with rating badges (`4.9 ★`), and upcoming booking shortcuts.
- **Explore & Filtering**: Search venues by name, sport, area, date, time slot, availability, price range, and rating.
- **Venue Detail**: High-quality sports photography, supported sports, operating hours, facilities (Parking, Lighting, Lockers, Wi-Fi), equipment breakdown (included free vs additional charge), venue rules, cancellation policy, customer reviews, and interactive time slot picker.
- **Booking Flow**: Select session slot -> Review booking summary -> Enter player details & equipment requirements -> `"Send booking request"` (`PENDING` state).
- **My Bookings**: Filter by `Upcoming`, `Pending`, `Past`, `Cancelled`. Unique booking references (e.g., `BK-20261012-0042`), cancellation option, and direct shortcut to open live match scoreboards or submit reviews.
- **Sport-Aware Live Scoreboards**: Live scoring interfaces tailored per sport:
  - **Cricket**: Runs, Wickets, Overs (e.g., `142/3 (18.2 ov)`).
  - **Football**: Home vs Away scores, Match timer (`68'`), Goal event timeline.
  - **Basketball**: Points, Quarters (`Q3`), Game clock.
  - **Badminton & Volleyball**: Sets and Points.
- **Notifications Feed**: Real notifications with unread badges, deep-linking directly to booking details, venues, or live scoreboards.

### 3. Venue Manager Experience
- **Operational Dashboard**: Managed arenas overview, pending requests counter, confirmed bookings, and quick actions (`"Add Session"`, `"Add Venue"`).
- **Schedule Grid**: Interactive calendar/time grid showing `CONFIRMED` bookings, `PENDING` requests, `BLOCKED` maintenance periods, and `FREE / AVAILABLE` slots.
- **Booking Requests Management**: Review player booking requests with complete player information, sport, slot, and equipment requested.
  - `"Approve"`: Confirms booking, updates session status to `BOOKED`, creates live match scoreboard, and sends notification to Player.
  - `"Reject"`: Rejection modal capturing reason (`Time unavailable`, `Venue maintenance`, `Already booked`, `Insufficient capacity`, `Other`).
- **Transactional Conflict Protection**: Automatically checks for time slot overlap upon approval. If another booking for the same slot was already confirmed, the second request is automatically rejected with explicit conflict notice to the player.

---

## Technical Stack & Architecture

- **Language & Framework**: Kotlin, Jetpack Compose, Material 3 Foundation
- **Local Persistence**: Room Database v3 (`ps22.db`) with `fallbackToDestructiveMigration`
- **Background Tasks**: WorkManager (24-hour and 1-hour pre-session notification alerts)
- **Design Tokens**: Primary Plum (`#542A52`), Peach Accent (`#FFB39A`), Dark Background (`#0B0D10`), Light Surface (`#F7F5F2`), Warm Surface (`#EFEAE5`)
- **Testing**: JUnit 4 & Coroutines Test

---

## Build & Test Commands

Run the following commands from the project root:

```powershell
# Execute unit tests
./gradlew.bat :app:testDebugUnitTest

# Assemble debug APK
./gradlew.bat :app:assembleDebug
```

---

## Data Schema & Headers

Key entity tables in `Ps22Database`:

- **`users`**: `id, googleAccountId, name, email, phone, photoUrl, role, createdAt, updatedAt`
- **`venues`**: `id, managerId, name, venueType, supportedSports, description, phone, email, address, area, city, operatingHours, facilities, rules, cancellationPolicy, coverImage, images, rating, reviewCount, createdAt`
- **`equipment`**: `id, venueId, sport, name, quantity, isIncludedFree, additionalCharge, notes`
- **`sessions`**: `id, venueId, sport, date, startTime, endTime, durationMinutes, capacity, bookedCount, price, status, createdAt`
- **`bookings`**: `id, bookingReference, playerId, playerName, playerEmail, playerPhone, playerCount, venueId, venueName, sessionId, sport, date, startTime, endTime, price, equipmentRequested, notes, status, rejectionReason, cancellationReason, createdAt, updatedAt`
- **`notifications`**: `id, userId, title, message, type, targetEntityId, isRead, timestamp`
- **`reviews`**: `id, venueId, bookingId, playerId, playerName, rating, comment, createdAt`
- **`match_scoreboards`**: `id, bookingId, venueId, sport, teamA, teamB, scoreA, scoreB, detailText, status, controllerUserId, updatedAt`
- **`scoreboard_events`**: `id, matchId, timestampText, eventType, description, createdAt`
