package Verdant.Vista.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.work.*
import Verdant.Vista.MainActivity
import Verdant.Vista.R
import Verdant.Vista.util.AppConstants
import Verdant.Vista.util.PhotoUtils
import java.util.concurrent.TimeUnit

class MemoryWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate: System requested update for ${appWidgetIds.size} widgets")
        scheduleNextUpdate(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: action=${intent.action}")
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val taxonId = intent.getIntExtra("taxon_id", -1)
            
            // Haptic Feedback: Give a subtle pulse to let the user know the tap registered
            try {
                val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                
                vibrator.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (_: Exception) {}

            // Visual Feedback: Show "Scouting for more wildlife..." instantly on the widget
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, MemoryWidget::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            for (appWidgetId in widgetIds) {
                // partiallyUpdateAppWidget is perfect here as we only want to change the status text
                // while keeping the current photo and names visible.
                val views = RemoteViews(context.packageName, R.layout.widget_memory)
                views.setTextViewText(R.id.widget_status_text, "Scouting for more wildlife...")
                views.setViewVisibility(R.id.widget_status_text, View.VISIBLE)
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
            }

            // "HOLD-CURRENT" STRATEGY:
            // We removed the showLoadingState call. This keeps the PREVIOUS 
            // species visible while the background worker scouts for the new one.
            // This makes the widget refresh feel instantaneous and eliminates the blank screen.
            
            triggerImmediateUpdate(context, taxonId)
        }
    }

    private fun showLoadingState(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = createBaseRemoteViews(context)
            views.setTextViewText(R.id.widget_common_name, "Loading...")
            views.setTextViewText(R.id.widget_scientific_name, "Scouting for the next species...")
            // Use full update to clear any stale state
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleNextUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WorkManager.getInstance(context).cancelUniqueWork(WIDGET_UPDATE_WORK)
    }

    private fun triggerImmediateUpdate(context: Context, taxonId: Int = -1) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val data = Data.Builder()
            .putInt("taxon_id", taxonId)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "WidgetManualRefresh",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    companion object {
        private const val TAG = "MemoryWidget"
        const val WIDGET_UPDATE_WORK = "VerdantVistaWidgetUpdate"
        const val ACTION_REFRESH = "VerdantVistaWidgetRefresh"

        fun updateWidgetLive(
            context: Context, 
            taxonId: Int, 
            commonName: String?, 
            scientificName: String?, 
            bitmap: Bitmap?
        ) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, MemoryWidget::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            // Clean up the name for the widget
            val rawName = commonName ?: scientificName ?: "Unknown Species"
            val capitalizedName = rawName.split(" ")
                .filter { it.isNotBlank() }
                .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
            
            // MASTER FIX: Combine background, overlay, and foreground into ONE bitmap
            // This is 100% safe for RemoteViews and prevents all layout/memory crashes.
            val cinematicBitmap = PhotoUtils.createCinematicWidgetBitmap(bitmap)
            
            for (appWidgetId in widgetIds) {
                try {
                    val views = createBaseRemoteViews(context, taxonId)
                    views.setTextViewText(R.id.widget_common_name, capitalizedName)
                    views.setTextViewText(R.id.widget_scientific_name, scientificName ?: "")
                    
                    if (cinematicBitmap != null) {
                        views.setImageViewBitmap(R.id.widget_image, cinematicBitmap)
                        // Clear the status text once the update is complete
                        views.setViewVisibility(R.id.widget_status_text, View.GONE)
                        views.setTextViewText(R.id.widget_status_text, "")
                    } else {
                        // "HOLD-CURRENT" STRATEGY:
                        // If no image is available, we do NOT reset to a placeholder.
                        // By returning here, we prevent updating the widget with empty data.
                        return
                    }
                    
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } catch (e: Exception) {
                    Log.e(TAG, "updateWidgetLive: CRITICAL FAILURE for widget $appWidgetId", e)
                }
            }
        }

        fun createBaseRemoteViews(context: Context, taxonId: Int? = null): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_memory)
            
            val prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
            val theme = prefs.getString(AppConstants.KEY_THEME, AppConstants.THEME_FOREST)
            
            val (titleColor, subColor) = when (theme) {
                AppConstants.THEME_DESERT -> context.getColor(R.color.desert_primary) to Color.parseColor("#FFE0B2")
                AppConstants.THEME_MIDNIGHT -> context.getColor(R.color.midnight_accent) to Color.parseColor("#E1BEE7")
                AppConstants.THEME_OCEAN -> context.getColor(R.color.ocean_primary) to Color.parseColor("#B2EBF2")
                AppConstants.THEME_SPRING -> context.getColor(R.color.spring_primary) to Color.parseColor("#FCE4EC")
                AppConstants.THEME_SUMMER -> context.getColor(R.color.summer_primary) to Color.parseColor("#FFF9C4")
                AppConstants.THEME_AUTUMN -> context.getColor(R.color.autumn_primary) to Color.parseColor("#FFCCBC")
                AppConstants.THEME_WINTER -> context.getColor(R.color.winter_primary) to Color.parseColor("#E3F2FD")
                else -> context.getColor(R.color.forest_primary) to Color.parseColor("#C8E6C9")
            }
            
            views.setTextColor(R.id.widget_common_name, titleColor)
            views.setTextColor(R.id.widget_scientific_name, subColor)

            val mainIntent = Intent(context, MainActivity::class.java).apply {
                action = "VIEW_DETAILS"
                taxonId?.let { putExtra("taxon_id", it) }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val mainPendingIntent = PendingIntent.getActivity(
                context, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, mainPendingIntent)

            val refreshIntent = Intent(context, MemoryWidget::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context, 2, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)
            
            return views
        }

        fun scheduleNextUpdate(context: Context) {
            val prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
            val intervalMinutes = prefs.getLong(AppConstants.KEY_UPDATE_INTERVAL, 60L).coerceAtLeast(15L)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WIDGET_UPDATE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }
    }
}
