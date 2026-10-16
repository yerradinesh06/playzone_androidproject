package com.ps22.tournament

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.ps22.tournament.ui.Ps22App
import com.ps22.tournament.ui.theme.DeepBackground
import com.ps22.tournament.ui.theme.LightSurface
import com.ps22.tournament.ui.theme.Ps22Theme

import androidx.fragment.app.FragmentActivity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ps22.tournament.notifications.SystemNotificationHelper

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SystemNotificationHelper.createNotificationChannel(this)
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        val app = application as Ps22Application
        setContent {
            var darkTheme by rememberSaveable { mutableStateOf(true) }
            SideEffect {
                val barColor = if (darkTheme) DeepBackground else LightSurface
                window.statusBarColor = barColor.toArgb()
                window.navigationBarColor = barColor.toArgb()
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            Ps22Theme(darkTheme = darkTheme) {
                Ps22App(
                    repository = app.repository,
                    venueRepo = app.venueBookingRepository,
                    isDarkTheme = darkTheme,
                    onToggleTheme = { darkTheme = !darkTheme },
                )
            }
        }
    }
}
