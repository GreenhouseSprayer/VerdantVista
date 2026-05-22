package Verdant.Vista.ui.widget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.*
import Verdant.Vista.MainActivity
import Verdant.Vista.R
import Verdant.Vista.VerdantVistaApplication
import Verdant.Vista.util.AppConstants
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class ProximityNotificationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val repository = (context.applicationContext as VerdantVistaApplication).repository
    private val prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "ProximityWorker"
        private const val CHANNEL_ID = "proximity_alerts"
        private const val NOTIFICATION_ID = 1001

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // 1. Schedule the 3-hour periodic scout
            val periodicRequest = PeriodicWorkRequestBuilder<ProximityNotificationWorker>(
                3, TimeUnit.HOURS
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "ProximityAlerts",
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )

            // 2. Trigger an "Immediate Scout" for instant feedback
            val oneTimeRequest = OneTimeWorkRequestBuilder<ProximityNotificationWorker>()
                .setConstraints(constraints)
                .build()
            
            WorkManager.getInstance(context).enqueue(oneTimeRequest)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("ProximityAlerts")
        }
    }

    override suspend fun doWork(): Result {
        if (!prefs.getBoolean(AppConstants.KEY_PROXIMITY_ENABLED, false)) {
            return Result.success()
        }

        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return Result.failure()
        }

        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
            val location = fusedLocationClient.lastLocation.await() ?: return Result.retry()

            // Use the DEDICATED Rare Scout for notifications
            val taxon = repository.fetchRareScout(location.latitude, location.longitude)

            if (taxon != null) {
                // Avoid notifying about the same species twice in a row
                val lastId = prefs.getInt(AppConstants.KEY_LAST_NOTIFICATION_TAXON, -1)
                if (taxon.id != lastId) {
                    val title = when {
                        taxon.isRare -> applicationContext.getString(R.string.notification_rare_title)
                        taxon.isSeasonalFirst -> applicationContext.getString(R.string.notification_seasonal_first_title)
                        else -> applicationContext.getString(R.string.notification_nearby_title)
                    }
                    sendNotification(taxon.commonName ?: taxon.name ?: "Interesting Species", title)
                    prefs.edit { putInt(AppConstants.KEY_LAST_NOTIFICATION_TAXON, taxon.id) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Proximity check failed", e)
            return Result.retry()
        }

        return Result.success()
    }

    private fun sendNotification(speciesName: String, title: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Missing POST_NOTIFICATIONS permission")
                return
            }
        }

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Channel creation is safe to call multiple times; system handles it as a no-op if exists.
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_star_black_24dp) // Use a better icon in production
            .setContentTitle(title)
            .setContentText(applicationContext.getString(R.string.notification_nearby_text, speciesName))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
