package Verdant.Vista.data.model

import Verdant.Vista.util.AppConstants
import com.google.gson.annotations.SerializedName

data class INaturalistResponse(
    val results: List<Observation>
)

data class ObservationResponse(
    val results: List<ObservationIdentifier>
)

data class ObservationIdentifier(
    val user: User,
    val count: Int
)

data class User(
    val id: Int,
    val login: String,
    val name: String?,
    @SerializedName("icon_url")
    val iconUrl: String?
)

data class Observation(
    val id: Long,
    val taxon: Taxon,
    @SerializedName("observation_photos")
    val photos: List<ObservationPhoto>,
    @SerializedName("observation_sounds")
    val sounds: List<ObservationSound>? = null,
    val votes: List<Any>? = null
)

data class Taxon(
    val id: Int,
    val name: String?, // Scientific Name
    @SerializedName("preferred_common_name")
    val commonName: String?,
    @SerializedName("default_photo")
    val defaultPhoto: Photo?,
    @SerializedName("taxon_photos")
    val taxonPhotos: List<TaxonPhoto>?,
    @SerializedName("wikipedia_summary")
    val summary: String?,
    val ancestors: List<Taxon>?,
    val rank: String?,
    @SerializedName("observations_count")
    val observationsCount: Int?,
    @SerializedName("wikipedia_url")
    val wikipediaUrl: String?,
    @SerializedName("conservation_status")
    val conservationStatus: ConservationStatus?,
    var isPeakSeason: Boolean = false,
    var isLocalFavorite: Boolean = false,
    var isRare: Boolean = false,
    var isSeasonalFirst: Boolean = false,
    @SerializedName("preferred_establishment_means")
    val establishmentMeans: String? = null
) {
    fun isNative(): Boolean = establishmentMeans?.lowercase() == "native" || establishmentMeans?.lowercase() == "endemic"
    fun isIntroduced(): Boolean = establishmentMeans?.lowercase() == "introduced"

    fun copyMetadataFrom(other: Taxon) {
        this.isPeakSeason = other.isPeakSeason
        this.isLocalFavorite = other.isLocalFavorite
        this.isRare = other.isRare
        this.isSeasonalFirst = other.isSeasonalFirst
    }
    fun isBird(): Boolean = hasAncestor(AppConstants.BIRD_TAXON_ID)
    fun isMammal(): Boolean = hasAncestor(AppConstants.MAMMAL_TAXON_ID)
    fun isReptile(): Boolean = hasAncestor(AppConstants.REPTILE_TAXON_ID)
    fun isAmphibian(): Boolean = hasAncestor(AppConstants.AMPHIBIAN_TAXON_ID)
    fun isInsect(): Boolean = hasAncestor(AppConstants.INSECT_TAXON_ID) || hasAncestor(47119) || hasAncestor(47120)
    fun isFungi(): Boolean = hasAncestor(AppConstants.FUNGI_TAXON_ID)
    fun isHerp(): Boolean = isReptile() || isAmphibian()
    fun isAquatic(): Boolean = hasAncestor(AppConstants.MOLLUSK_TAXON_ID) || hasAncestor(AppConstants.FISH_TAXON_ID)

    private fun hasAncestor(ancestorId: Int): Boolean {
        if (id == ancestorId) return true
        return ancestors?.any { it.id == ancestorId } ?: false
    }
}

data class ConservationStatus(
    val status: String?,
    val authority: String?,
    @SerializedName("status_name")
    val statusName: String?
)

data class ObservationPhoto(
    val photo: Photo
)

data class TaxonPhoto(
    val photo: Photo
)

data class ObservationSound(
    val sound: Sound
)

data class Sound(
    val id: Int,
    @SerializedName("file_url")
    val fileUrl: String,
    @SerializedName("file_content_type")
    val contentType: String?,
    @SerializedName("duration_ms")
    val durationMs: Int? = null
)

data class Photo(
    @SerializedName("url")
    val url: String?,
    @SerializedName("medium_url")
    val mediumUrl: String?,
    @SerializedName("large_url")
    val largeUrl: String?,
    @SerializedName("original_url")
    val originalUrl: String?,
    @SerializedName("original_dimensions")
    val dimensions: Dimensions?,
    @SerializedName("attribution")
    val attribution: String? = null,
    @SerializedName("license_code")
    val licenseCode: String? = null
)

data class Dimensions(
    val width: Int?,
    val height: Int?
)
