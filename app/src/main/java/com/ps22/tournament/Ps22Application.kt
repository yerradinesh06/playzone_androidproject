package com.ps22.tournament

import android.app.Application
import androidx.room.Room
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.ps22.tournament.data.MIGRATION_1_2
import com.ps22.tournament.data.Ps22Database
import com.ps22.tournament.data.TournamentRepository
import com.ps22.tournament.data.VenueBookingRepository

class Ps22Application : Application() {
    lateinit var database: Ps22Database
        private set
    lateinit var repository: TournamentRepository
        private set
    lateinit var venueBookingRepository: VenueBookingRepository
        private set

    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isEmpty()) {
            runCatching {
                if (BuildConfig.FIREBASE_CONFIGURED) {
                    FirebaseApp.initializeApp(
                        this,
                        FirebaseOptions.Builder()
                            .setApiKey(BuildConfig.FIREBASE_API_KEY)
                            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                            .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                            .build(),
                    )
                } else {
                    FirebaseApp.initializeApp(this)
                }
            }
        }
        database = Room.databaseBuilder(this, Ps22Database::class.java, "ps22.db")
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
        repository = TournamentRepository(database, this)
        venueBookingRepository = VenueBookingRepository(database, this)
    }
}
