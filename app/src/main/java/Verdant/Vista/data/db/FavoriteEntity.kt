package Verdant.Vista.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: Long,
    val scientificName: String,
    val commonName: String?,
    val imageUrl: String,
    val taxonType: String, // birds, plants, insects
    val summary: String?,
    val taxonomy: String?,
    val ancestorIds: String? = null
)
