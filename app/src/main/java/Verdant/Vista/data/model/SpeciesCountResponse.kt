package Verdant.Vista.data.model

import com.google.gson.annotations.SerializedName

data class SpeciesCountResponse(
    val results: List<SpeciesCount>
)

data class SpeciesCount(
    val count: Int,
    val taxon: Taxon
)
