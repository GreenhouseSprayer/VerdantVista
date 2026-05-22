package Verdant.Vista.data.api

import Verdant.Vista.data.model.INaturalistResponse
import Verdant.Vista.data.model.SpeciesCountResponse
import Verdant.Vista.data.model.TaxonResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface INaturalistService {
    @Headers("User-Agent: VerdantVista/1.0 (https://github.com/VerdantVista)")
    @GET("observations")
    suspend fun getObservations(
        @Query("taxon_id") taxonId: String? = null,
        @Query("per_page") perPage: Int = 1,
        @Query("page") page: Int? = null,
        @Query("order_by") orderBy: String = "observations_count",
        @Query("quality_grade") qualityGrade: String? = null,
        @Query("verifiable") verifiable: Boolean? = null,
        @Query("photos") photos: Boolean? = null,
        @Query("sounds") sounds: Boolean? = null,
        @Query("popular") popular: Boolean? = null,
        @Query("place_id") placeId: Int? = null,
        @Query("month") month: Int? = null
    ): INaturalistResponse

    @Headers("User-Agent: VerdantVista/1.0 (https://github.com/VerdantVista)")
    @GET("observations/species_counts")
    suspend fun getSpeciesCounts(
        @Query("place_id") placeId: Int? = null,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
        @Query("radius") radius: Int? = null,
        @Query("taxon_id") taxonId: String? = null,
        @Query("month") month: Int? = null,
        @Query("per_page") perPage: Int = 200,
        @Query("quality_grade") qualityGrade: String = "research",
        @Query("verifiable") verifiable: Boolean? = null,
        @Query("native") native: Boolean? = null,
        @Query("introduced") introduced: Boolean? = null,
        @Header("Cache-Control") cacheControl: String? = null // For emergency bypass
    ): SpeciesCountResponse

    @Headers("User-Agent: VerdantVista/1.0 (https://github.com/VerdantVista)")
    @GET("taxa")
    suspend fun searchTaxa(
        @Query("q") query: String? = null,
        @Query("taxon_id") taxonId: String? = null,
        @Query("place_id") placeId: Int? = null,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
        @Query("radius") radius: Int? = null,
        @Query("per_page") perPage: Int = 20,
        @Query("rank") rank: String = "species",
        @Query("is_active") isActive: Boolean = true,
        @Header("Cache-Control") cacheControl: String? = null
    ): TaxonResponse

    @Headers("User-Agent: VerdantVista/1.0 (https://github.com/VerdantVista)")
    @GET("taxa/{id}")
    suspend fun getTaxonDetails(
        @Path("id") id: Int,
        @Query("preferred_place_id") preferredPlaceId: Int? = null
    ): TaxonResponse
}
