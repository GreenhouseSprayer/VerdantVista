package Verdant.Vista.ui.transform

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import Verdant.Vista.VerdantVistaApplication
import Verdant.Vista.data.model.Taxon
import Verdant.Vista.data.repository.ObservationRepository
import Verdant.Vista.util.AppConstants
import Verdant.Vista.util.NetworkUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class TransformViewModel(application: Application) : AndroidViewModel(application) {

    private val _discoveredTaxon = MutableLiveData<Taxon?>()
    val discoveredTaxon: LiveData<Taxon?> = _discoveredTaxon

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val repository: ObservationRepository = (application as VerdantVistaApplication).repository
    private val prefs = application.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
    
    private var fetchJob: Job? = null

    companion object {
        private const val TAG = "TransformViewModel"
    }

    fun fetchTaxonById(taxonId: Int) {
        fetchJob?.cancel()
        
        if (_discoveredTaxon.value?.id == taxonId) return

        _isLoading.value = true
        fetchJob = viewModelScope.launch {
            try {
                val currentPlaceId = prefs.getInt(AppConstants.KEY_PLACE_ID, 1).let { if (it <= 0) 1 else it }
                val detailedTaxon = repository.getTaxonDetails(taxonId, currentPlaceId)
                _discoveredTaxon.value = detailedTaxon
                detailedTaxon?.id?.let { repository.addToHistory(it, prefs) }
            } catch (e: Exception) {
                Log.e(TAG, "fetchTaxonById: Exception", e)
                _discoveredTaxon.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchRandomPlant() {
        if (_isLoading.value == true) return

        _isLoading.value = true
        fetchJob = viewModelScope.launch {
            try {
                val context = getApplication<Application>().applicationContext
                val isConnected = NetworkUtils.isConnected(context)
                val currentPlaceId = prefs.getInt(AppConstants.KEY_PLACE_ID, 1).let { if (it <= 0) 1 else it }

                // 1. Try Seasonal/Historical Discovery
                var winner: Taxon? = null
                if (isConnected) {
                    val lat = prefs.getString("last_lat", null)?.toDoubleOrNull()
                    val lng = prefs.getString("last_lng", null)?.toDoubleOrNull()
                    
                    val selectedCategories = prefs.getStringSet(AppConstants.KEY_TAXON_TYPES, setOf(AppConstants.TAXON_PLANTS)) ?: setOf(AppConstants.TAXON_PLANTS)
                    val taxonIdString = selectedCategories.mapNotNull { category ->
                        AppConstants.getTaxonIdString(category)
                    }.joinToString(",")

                    winner = repository.fetchSeasonalForecast(
                        placeId = if (currentPlaceId > 1) currentPlaceId else null,
                        lat = if (currentPlaceId == 1) lat else null, // Use GPS if worldwide/default
                        lng = if (currentPlaceId == 1) lng else null,
                        taxonIds = taxonIdString
                    )
                }

                // 2. Fallback to general discovery
                if (winner == null) {
                    winner = repository.fetchDiscoverWithFallbacks(prefs, allowApi = isConnected)
                }

                if (winner != null) {
                    // Update main UI instantly with the "Scouted" winner (has badges)
                    _discoveredTaxon.value = winner
                    repository.addToHistory(winner.id, prefs)
                    
                    // 3. Load background details (Wiki/Family Tree) while preserving badges
                    if (isConnected) {
                        val scoutedWinner = winner 
                        viewModelScope.launch {
                            try {
                                val detailedTaxon = repository.getTaxonDetails(scoutedWinner.id, currentPlaceId)
                                if (detailedTaxon != null && _discoveredTaxon.value?.id == scoutedWinner.id) {
                                    
                                    // --- THE "UNKNOWN SPECIES" FIREWALL ---
                                    // Only allow the update if the new data actually has an identity.
                                    // If it's null or unknown, we keep the scoutedWinner's identity.
                                    val hasIdentity = !detailedTaxon.commonName.isNullOrBlank() || !detailedTaxon.name.isNullOrBlank()
                                    
                                    if (hasIdentity) {
                                        // PATCH: Transfer the badges from the scouted winner to the detailed one
                                        detailedTaxon.copyMetadataFrom(scoutedWinner)
                                        
                                        Log.d(TAG, "Publishing patched detailed taxon: ${detailedTaxon.getDisplayName()}")
                                        _discoveredTaxon.postValue(detailedTaxon)
                                    } else {
                                        // DEEP PATCH: If details missing names, we merge everything into the EXISTING winner
                                        Log.w(TAG, "Details missing identity, performing Deep Patch onto existing winner")
                                        scoutedWinner.summary = detailedTaxon.summary
                                        scoutedWinner.ancestors = detailedTaxon.ancestors
                                        scoutedWinner.taxonPhotos = detailedTaxon.taxonPhotos
                                        
                                        // Re-publish the winner with its original name but NEW deep data
                                        _discoveredTaxon.postValue(scoutedWinner)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Background details fetch failed", e)
                            }
                        }
                    }
                } else {
                    _discoveredTaxon.value = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchRandomPlant: Exception", e)
                _discoveredTaxon.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }
}
