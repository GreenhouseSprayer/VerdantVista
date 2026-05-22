package Verdant.Vista.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface WikipediaService {
    /**
     * Fetches the full article content.
     * explaintext=1 removes HTML tags for a clean string.
     */
    @Headers("User-Agent: VerdantVistaApp/1.0 (https://github.com/VerdantVista)")
    @GET("w/api.php?action=query&format=json&prop=extracts&explaintext=1&redirects=1")
    suspend fun getFullSummary(
        @Query("titles") title: String
    ): WikipediaQueryResponse
}

data class WikipediaQueryResponse(
    @SerializedName("query")
    val query: WikipediaQuery?
)

data class WikipediaQuery(
    @SerializedName("pages")
    val pages: Map<String, WikipediaPage>?
)

data class WikipediaPage(
    @SerializedName("extract")
    val extract: String?
)
