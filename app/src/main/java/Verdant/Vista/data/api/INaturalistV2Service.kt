package Verdant.Vista.data.api

import Verdant.Vista.data.model.INaturalistResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Modern iNaturalist API v2 service.
 * Used for high-efficiency seasonal forecasting and local scouting.
 */
interface INaturalistV2Service {

    @GET("observations")
    suspend fun getObservations(
        @Query("place_id") placeId: Int? = null,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
        @Query("radius") radius: Int? = null,
        @Query("taxon_id") taxonId: String? = null,
        @Query("month") month: Int? = null,
        @Query("per_page") perPage: Int = 10,
        @Query("order_by") orderBy: String = "votes",
        @Query("quality_grade") qualityGrade: String = "research",
        @Query("verifiable") verifiable: Boolean = true,
        @Query("photos") photos: Boolean = true,
        @Header("X-Fields") fields: String? = null // Using header for fields as it's cleaner than RISON query in some Retrofit versions
    ): INaturalistResponse

    @GET("observations/species_counts")
    suspend fun getSpeciesCounts(
        @Query("place_id") placeId: Int? = null,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
        @Query("radius") radius: Int? = null,
        @Query("month") month: Int? = null,
        @Query("per_page") perPage: Int = 100,
        @Query("quality_grade") qualityGrade: String = "research",
        @Query("taxon_id") taxonIds: String? = null,
        @Query("preferred_place_id") preferredPlaceId: Int? = null, // CRITICAL FOR NATIVE STATUS
        @Header("X-Fields") fields: String? = null,
        @Header("Cache-Control") cacheControl: String? = null
    ): Verdant.Vista.data.model.SpeciesCountResponse
}
