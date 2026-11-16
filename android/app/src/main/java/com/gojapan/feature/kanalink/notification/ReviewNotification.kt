package com.gojapan.feature.kanalink.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gojapan.MainActivity
import com.gojapan.R
import com.gojapan.feature.kanalink.data.local.KanaLinkDatabase
import com.gojapan.feature.kanalink.data.local.DatabaseRecovery
import com.gojapan.feature.kanalink.data.local.MIGRATION_1_2
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "kana-review"
private const val UNIQUE_WORK = "kana-review-check"

class ReviewNotificationScheduler(private val context: Context) {
    fun setEnabled(enabled: Boolean) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        context.getSystemService(NotificationManager::class.java).cancel(104)
    }
}

class ReviewNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = Result.success()
}
