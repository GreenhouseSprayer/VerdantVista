package Verdant.Vista.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "discovery_cache")
data class DiscoveryEntity(
    @PrimaryKey val id: Long,
    val scientificName: String,
    val commonName: String?,
    val imageUrl: String,
    val imageData: ByteArray?,
    val attribution: String?, // Photographer attribution
    val summary: String?,
    val taxonType: String,
    val taxonomy: String?,
    val ancestorIds: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DiscoveryEntity
        if (id != other.id) return false
        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
