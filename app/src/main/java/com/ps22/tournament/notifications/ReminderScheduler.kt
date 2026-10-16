package com.ps22.tournament.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ps22.tournament.data.FixtureEntity
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    fun schedule(context: Context, fixtures: List<FixtureEntity>) {
        val work = WorkManager.getInstance(context)
        val now = System.currentTimeMillis()
        fixtures.filter { it.status == "SCHEDULED" && it.kickoffAt > now }.forEach { fixture ->
            // 24 hour pre-match reminder
            val delay24h = (fixture.kickoffAt - now - 24 * 60 * 60 * 1000L).coerceAtLeast(0)
            val req24h = OneTimeWorkRequestBuilder<FixtureReminderWorker>()
                .setInitialDelay(delay24h, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("fixtureId" to fixture.id, "round" to fixture.round, "timing" to "24 Hours Before Kickoff"))
                .addTag("fixture-reminder-24h")
                .build()
            work.enqueueUniqueWork("ps22-reminder-24h-${fixture.id}", ExistingWorkPolicy.REPLACE, req24h)

            // 1 hour pre-match reminder
            val delay1h = (fixture.kickoffAt - now - 1 * 60 * 60 * 1000L).coerceAtLeast(0)
            val req1h = OneTimeWorkRequestBuilder<FixtureReminderWorker>()
                .setInitialDelay(delay1h, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("fixtureId" to fixture.id, "round" to fixture.round, "timing" to "1 Hour Before Kickoff"))
                .addTag("fixture-reminder-1h")
                .build()
            work.enqueueUniqueWork("ps22-reminder-1h-${fixture.id}", ExistingWorkPolicy.REPLACE, req1h)
        }
    }
}

class FixtureReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Match Reminders", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val round = inputData.getString("round") ?: "Matchday Fixture"
        val timing = inputData.getString("timing") ?: "Upcoming Kickoff"
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("PS 22 · $timing")
            .setContentText("$round is starting soon. Check PS 22 for line-ups and live scores.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify((inputData.getString("fixtureId") + timing).hashCode(), notification)
        }
        return Result.success()
    }

    private companion object { const val CHANNEL = "ps22-match-reminders" }
}
