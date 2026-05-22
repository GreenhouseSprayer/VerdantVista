package Verdant.Vista.ui.widget

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import Verdant.Vista.VerdantVistaApplication
import Verdant.Vista.data.repository.ObservationRepository
import Verdant.Vista.util.AppConstants
import Verdant.Vista.util.PhotoUtils
import Verdant.Vista.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WidgetUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val repository: ObservationRepository = (context.applicationContext as VerdantVistaApplication).repository

    companion object {
        private const val TAG = "WidgetUpdateWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "doWork: Starting widget update worker")
        val prefs = applicationContext.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        
        val specificTaxonId = inputData.getInt("taxon_id", -1)
        if (specificTaxonId != -1) {
            Log.d(TAG, "doWork: Fetching specific taxon $specificTaxonId")
            val success = updateWithTaxonId(specificTaxonId)
            return@withContext if (success) Result.success() else Result.failure()
        }

        val isConnected = NetworkUtils.isConnected(applicationContext)
        
        try {
            // HIGH RELIABILITY FLOW:
            // 1. Try to get a fresh discovery list from API (if connected)
            var shallowTaxon = if (isConnected) {
                try {
                    repository.fetchDiscoverSpecies(prefs, 200)
                } catch (e: Exception) {
                    Log.w(TAG, "doWork: API discovery failed, will try cache", e)
                    null
                }
            } else null
            
            // 2. If API failed or offline, pull from the Discovery Vault (Layer 2)
            if (shallowTaxon == null) {
                Log.d(TAG, "doWork: Pulling from local vault...")
                shallowTaxon = repository.fetchDiscoverWithFallbacks(prefs, allowApi = false)
            }
            
            if (shallowTaxon != null) {
                // HIGH RELIABILITY: Try to get the pre-downloaded image from the cache first.
                // This bypasses the carrier data block entirely.
                val cachedEntity = repository.getDiscoveryDao().getDiscoveryById(shallowTaxon.id.toLong())
                val cachedBitmap = PhotoUtils.byteArrayToBitmap(cachedEntity?.imageData)

                val bitmap = if (cachedBitmap != null) {
                    Log.d(TAG, "doWork: Using high-speed local image for ${shallowTaxon.name}")
                    cachedBitmap
                } else {
                    // Fallback to network if cache is cold
                    val imageUrl = shallowTaxon.defaultPhoto?.mediumUrl ?: shallowTaxon.defaultPhoto?.url
                    imageUrl?.let { PhotoUtils.fetchBitmap(applicationContext, it) }
                }

                // PUSH UPDATE: Use the data we have right now.
                MemoryWidget.updateWidgetLive(
                    applicationContext,
                    shallowTaxon.id,
                    shallowTaxon.commonName ?: shallowTaxon.name,
                    shallowTaxon.name,
                    bitmap
                )
                
                repository.addToHistory(shallowTaxon.id, prefs)
                Result.success()
            } else {
                Log.e(TAG, "doWork: Total failure - vault is empty and API is unreachable. KEEPING CURRENT.")
                // "HOLD-CURRENT" STRATEGY:
                // We no longer call resetWidgetToPlaceholder().
                // By doing nothing, the widget maintains the previous species photo/name.
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "doWork: Critical error in widget worker. KEEPING CURRENT.", e)
            // "HOLD-CURRENT" STRATEGY:
            // Maintain the current screen state rather than showing a placeholder.
            Result.failure()
        }
    }

    private fun resetWidgetToPlaceholder() {
        MemoryWidget.updateWidgetLive(
            applicationContext,
            -1,
            "Discover Nature",
            "Tap to refresh",
            null
        )
    }

    private suspend fun updateWithTaxonId(taxonId: Int): Boolean {
        return try {
            val taxon = repository.getTaxonDetails(taxonId) ?: return false
            val imageUrl = taxon.defaultPhoto?.mediumUrl ?: taxon.defaultPhoto?.url
            val bitmap = imageUrl?.let { PhotoUtils.fetchBitmap(applicationContext, it) }

            MemoryWidget.updateWidgetLive(
                applicationContext,
                taxon.id,
                taxon.commonName ?: taxon.name,
                taxon.name,
                bitmap
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "updateWithTaxonId failed", e)
            false
        }
    }
}
