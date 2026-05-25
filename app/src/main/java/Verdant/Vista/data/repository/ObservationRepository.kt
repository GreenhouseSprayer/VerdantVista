package Verdant.Vista.data.repository

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.util.LruCache
import androidx.core.content.edit
import Verdant.Vista.data.api.INaturalistService
import Verdant.Vista.data.api.INaturalistV2Service
import Verdant.Vista.data.api.WikipediaService
import Verdant.Vista.data.db.DiscoveryDao
import Verdant.Vista.data.db.DiscoveryEntity
import Verdant.Vista.data.db.FavoriteDao
import Verdant.Vista.data.db.FavoriteEntity
import Verdant.Vista.data.model.Photo
import Verdant.Vista.data.model.SpeciesCount
import Verdant.Vista.data.model.Taxon
import Verdant.Vista.ui.widget.MemoryWidget
import Verdant.Vista.util.AppConstants
import Verdant.Vista.util.PhotoUtils
import Verdant.Vista.util.WikiUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

class ObservationRepository(
    private val apiService: INaturalistService,
    private val apiV2Service: INaturalistV2Service,
    private val wikipediaService: WikipediaService,
    private val favoriteDao: FavoriteDao,
    private val discoveryDao: DiscoveryDao,
    private val application: Application
) {

    fun getDiscoveryDao() = discoveryDao

    suspend fun clearDiscoveryVault() {
        discoveryDao.clearVault()
    }

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    private val taxonCache = LruCache<Int, Taxon>(20)
    private val photosCache = LruCache<Int, List<String>>(20)
    private val attributionCache = LruCache<Int, List<String?>>(20)
    private val soundsCache = LruCache<Int, List<Pair<String, Int>>>(20)

    companion object {
        private const val TAG = "ObservationRepository"
        private const val QUALITY_RESEARCH = "research"
        private const val PREF_RECENT_SPECIES = "recent_species"
        private const val RECENT_HISTORY_SIZE = 50
        private const val MIN_WIDTH_QUALITY = 1200 
    }

    fun isRecentlyShown(taxonId: Int, prefs: SharedPreferences): Boolean {
        val history = prefs.getString(PREF_RECENT_SPECIES, "") ?: ""
        return history.split(",").contains(taxonId.toString())
    }

    fun addToHistory(taxonId: Int, prefs: SharedPreferences) {
        val history = prefs.getString(PREF_RECENT_SPECIES, "") ?: ""
        val historyList = history.split(",").filter { it.isNotEmpty() }.toMutableList()
        historyList.remove(taxonId.toString())
        historyList.add(taxonId.toString())
        if (historyList.size > RECENT_HISTORY_SIZE) {
            historyList.removeAt(0)
        }
        prefs.edit { putString(PREF_RECENT_SPECIES, historyList.joinToString(",")) }
    }

    suspend fun getTaxonDetails(taxonId: Int, preferredPlaceId: Int? = 1): Taxon? {
        taxonCache.get(taxonId)?.let { return it }
        return try {
            // BACK TO v1: It is more reliable for full taxonomy and descriptions
            val response = apiService.getTaxonDetails(taxonId, preferredPlaceId)
            val result = response.results.firstOrNull()
            
            if (result != null) {
                Log.d(TAG, "getTaxonDetails v1 success: ${result.getDisplayName()}")
                taxonCache.put(taxonId, result)
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "getTaxonDetails v1 failed", e)
            null
        }
    }

    suspend fun fetchWikiSummary(taxon: Taxon): String? {
        val searchTitles = listOfNotNull(taxon.name, taxon.commonName)
        for (title in searchTitles) {
            try {
                val wikiResponse = wikipediaService.getFullSummary(title)
                val extract = wikiResponse.query?.pages?.values?.firstOrNull()?.extract
                if (!extract.isNullOrBlank() && extract.length > 50) {
                    return WikiUtils.cleanExtract(extract)
                }
            } catch (_: Exception) {}
        }
        return null
    }

    suspend fun fetchSeasonalForecast(
        placeId: Int?,
        lat: Double? = null,
        lng: Double? = null,
        taxonIds: String? = null
    ): Taxon? {
        val month = Calendar.getInstance().get(Calendar.MONTH) + 1
        
        try {
            Log.d(TAG, "App Scout: Searching for $taxonIds in 100 miles")
            val historicalResponse = apiV2Service.getSpeciesCounts(
                placeId = if (placeId != null && placeId > 0) placeId else null,
                lat = lat,
                lng = lng,
                radius = if (lat != null) 160 else null, 
                month = month,
                taxonIds = taxonIds,
                perPage = 500, 
                fields = "(count:!t,taxon:(id:!t,name:!t,preferred_common_name:!t,observations_count:!t,wikipedia_summary:!t,preferred_establishment_means:!t,default_photo:(url:!t,attribution:!t,medium_url:!t),ancestors:(id:!t)))",
                cacheControl = "no-cache"
            )

            if (historicalResponse.results.isNotEmpty()) {
                val prefs = application.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
                
                val verifiedPool = historicalResponse.results
                    .map { it.taxon }
                    .distinctBy { it.id }
                    .filter { taxon ->
                        val ancestors = taxon.ancestors?.map { it.id } ?: emptyList()
                        val isFish = taxon.id == 47178 || ancestors.contains(47178)
                        val isCrustacean = taxon.id == 85497 || ancestors.contains(85497)
                        val isMollusk = taxon.id == 47115 || ancestors.contains(47115)
                        val isAquaticFilter = taxonIds?.contains("47178") == true || taxonIds?.contains("85497") == true
                        if (isAquaticFilter) (isFish || isCrustacean || isMollusk) else true
                    }

                val history = prefs.getString(PREF_RECENT_SPECIES, "") ?: ""
                val historySet = history.split(",").filter { it.isNotEmpty() }.toSet()
                val trulyNewPool = verifiedPool.filter { !historySet.contains(it.id.toString()) }
                
                if (trulyNewPool.isNotEmpty()) {
                    val winner = trulyNewPool.shuffled().first()
                    val finalTaxon = if (winner.commonName.isNullOrBlank() && winner.name.isNullOrBlank()) {
                        getTaxonDetails(winner.id) ?: winner
                    } else {
                        winner
                    }
                    finalTaxon.isPeakSeason = true
                    return finalTaxon
                } else if (verifiedPool.isNotEmpty()) {
                    prefs.edit { putString(PREF_RECENT_SPECIES, "") }
                    return verifiedPool.shuffled().first()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchSeasonalForecast failed", e)
        }
        return null
    }

    suspend fun fetchRareScout(lat: Double, lng: Double): Taxon? {
        try {
            val rareResponse = apiV2Service.getSpeciesCounts(
                lat = lat,
                lng = lng,
                radius = 160,
                taxonIds = null, 
                fields = "(count:!t,taxon:(id:!t,name:!t,preferred_common_name:!t,observations_count:!t,default_photo:(url:!t,medium_url:!t)))"
            )
            if (rareResponse.results.isNotEmpty()) {
                val rarestTaxa = rareResponse.results
                    .map { it.taxon }
                    .distinctBy { it.id }
                    .filter { (it.observationsCount ?: 0) > 0 }
                    .sortedBy { it.observationsCount }
                    .take(15) 
                if (rarestTaxa.isNotEmpty()) {
                    val selected = rarestTaxa.shuffled().first()
                    selected.isRare = true
                    return selected
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchRareScout failed", e)
        }
        return null
    }

    suspend fun fetchRandomSpecies(
        placeId: Int?,
        lat: Double? = null,
        lng: Double? = null,
        taxonId: String,
        month: Int?,
        perPage: Int
    ): List<SpeciesCount>? {
        if (placeId != null || (lat != null && lng != null)) {
            try {
                val response = apiService.getSpeciesCounts(
                    placeId = placeId,
                    lat = lat,
                    lng = lng,
                    radius = if (lat != null) 30 else null,
                    taxonId = taxonId,
                    month = month,
                    perPage = perPage,
                    qualityGrade = QUALITY_RESEARCH,
                    native = null,
                    introduced = null,
                    cacheControl = "no-cache"
                )
                if (response.results.isNotEmpty()) return response.results
            } catch (_: Exception) {}
        }
        return null
    }

    suspend fun fetchDiscoverSpecies(prefs: SharedPreferences, perPage: Int): Taxon? {
        val selectedCategories = prefs.getStringSet(AppConstants.KEY_TAXON_TYPES, setOf(AppConstants.TAXON_PLANTS)) ?: setOf(AppConstants.TAXON_PLANTS)
        val taxonIdString = selectedCategories.mapNotNull { category -> AppConstants.getTaxonIdString(category) }.joinToString(",")
        val placeIdPref = prefs.getInt(AppConstants.KEY_PLACE_ID, -1)
        val lat = prefs.getString("last_lat", null)?.toDoubleOrNull()
        val lng = prefs.getString("last_lng", null)?.toDoubleOrNull()
        val placeId = if (placeIdPref > 0) placeIdPref else null
        val month = Calendar.getInstance().get(Calendar.MONTH) + 1
        val response = try { fetchRandomSpecies(placeId, lat, lng, taxonIdString, month, perPage) } catch (e: Exception) { null }
        if (response != null && response.isNotEmpty()) {
            return processDiscoveryList(response.map { it.taxon }, taxonIdString, prefs)
        }
        try {
            val searchResponse = apiService.searchTaxa(
                taxonId = taxonIdString,
                placeId = placeId,
                lat = lat,
                lng = lng,
                radius = if (lat != null) 100 else null,
                perPage = 50
            )
            if (searchResponse.results.isNotEmpty()) return processDiscoveryList(searchResponse.results, taxonIdString, prefs)
        } catch (_: Exception) {}
        return null
    }

    private suspend fun processDiscoveryList(taxa: List<Taxon>, taxonType: String, prefs: SharedPreferences): Taxon? {
        val entities = taxa.map {
            DiscoveryEntity(it.id.toLong(), it.name ?: "Unknown", it.commonName, it.defaultPhoto?.url ?: "", null, it.defaultPhoto?.attribution, it.summary, taxonType, null, it.ancestors?.map { ancestor -> ancestor.id }?.joinToString(","), System.currentTimeMillis())
        }
        discoveryDao.insertAllDiscovery(entities)
        val shuffledList = taxa.shuffled()
        return shuffledList.find { !isRecentlyShown(it.id, prefs) } ?: shuffledList.firstOrNull()
    }

    suspend fun fetchDiscoverWithFallbacks(prefs: SharedPreferences, allowApi: Boolean = true): Taxon? {
        if (allowApi) {
            try { fetchDiscoverSpecies(prefs, 200)?.let { return it } } catch (_: Exception) {}
        }
        try {
            val cached = discoveryDao.getRandomDiscovery()
            if (cached != null) return Taxon(cached.id.toInt(), cached.scientificName, cached.commonName, Photo(cached.imageUrl, cached.imageUrl, null, null, null, cached.attribution), null, cached.summary, emptyList(), null, null, null, null)
        } catch (_: Exception) {}
        return getTaxonDetails(AppConstants.DEFAULT_SPECIES_IDS.random())
    }

    suspend fun getResearchGradePhotos(taxonId: Int): Pair<List<String>, List<String?>> {
        photosCache.get(taxonId)?.let { return it to (attributionCache.get(taxonId) ?: emptyList()) }
        val photoData = mutableListOf<Triple<String, Int, String?>>()
        val seenUrls = mutableSetOf<String>()
        fun addPhoto(photo: Photo) {
            val rawUrl = photo.originalUrl ?: photo.largeUrl ?: photo.mediumUrl ?: photo.url ?: return
            val highResUrl = PhotoUtils.getHighResUrl(rawUrl) ?: return
            if (seenUrls.add(highResUrl)) {
                val pixels = photo.dimensions?.let { (it.width ?: 0) * (it.height ?: 0) } ?: 0
                photoData.add(Triple(highResUrl, pixels, photo.attribution))
            }
        }
        try {
            getTaxonDetails(taxonId)?.taxonPhotos?.forEach { addPhoto(it.photo) }
            val response = apiService.getObservations(
                taxonId = taxonId.toString(),
                perPage = 50,
                qualityGrade = QUALITY_RESEARCH,
                photos = true,
                popular = true
            )
            for (obs in response.results) { obs.photos.firstOrNull()?.photo?.let { addPhoto(it) } }
        } catch (_: Exception) {}
        val sortedData = photoData.sortedByDescending { it.second }.take(10)
        val finalPhotos = sortedData.map { it.first }
        val finalAttributions = sortedData.map { it.third }
        if (finalPhotos.isNotEmpty()) {
            photosCache.put(taxonId, finalPhotos)
            attributionCache.put(taxonId, finalAttributions)
        }
        return finalPhotos to finalAttributions
    }
    
    suspend fun getBirdSounds(taxonId: Int): List<Pair<String, Int>> {
        soundsCache.get(taxonId)?.let { return it }
        val sounds = mutableListOf<Pair<String, Int>>()
        try {
            val response = apiService.getObservations(
                taxonId = taxonId.toString(),
                perPage = 20,
                qualityGrade = QUALITY_RESEARCH,
                sounds = true
            )
            for (obs in response.results) {
                obs.sounds?.firstOrNull()?.sound?.let { 
                    if (sounds.none { s -> s.first == it.fileUrl }) sounds.add(it.fileUrl to (it.durationMs ?: 0))
                }
                if (sounds.size >= 5) break
            }
        } catch (_: Exception) {}
        if (sounds.isNotEmpty()) soundsCache.put(taxonId, sounds)
        return sounds
    }

    fun getFavorites(): Flow<List<FavoriteEntity>> = favoriteDao.getAllFavorites()
    suspend fun getFavoritesOnce(): List<FavoriteEntity> = favoriteDao.getAllFavoritesOnce()
    suspend fun isFavorite(id: Long): Boolean = favoriteDao.getFavoriteById(id) != null
    fun isFavoriteFlow(id: Long): Flow<Boolean> = favoriteDao.isFavoriteFlow(id)
    suspend fun toggleFavorite(favorite: FavoriteEntity, application: Application) {
        val existing = favoriteDao.getFavoriteById(favorite.id)
        if (existing == null) favoriteDao.insertFavorite(favorite) else favoriteDao.deleteFavorite(existing)
        val intent = Intent(application, MemoryWidget::class.java).apply { action = MemoryWidget.ACTION_REFRESH; putExtra("taxon_id", favorite.id.toInt()) }
        application.sendBroadcast(intent)
    }

    suspend fun fetchSeasonalSpecies(placeId: Int?, taxonId: String?, month: Int = Calendar.getInstance().get(Calendar.MONTH) + 1, perPage: Int = 50): List<SpeciesCount>? {
        return try { apiService.getSpeciesCounts(placeId, null, null, null, taxonId, month, perPage, QUALITY_RESEARCH, true).results } catch (e: Exception) { null }
    }
}
