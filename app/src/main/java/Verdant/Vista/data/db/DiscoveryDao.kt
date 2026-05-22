package Verdant.Vista.data.db

import androidx.room.*

@Dao
interface DiscoveryDao {
    @Query("SELECT * FROM discovery_cache ORDER BY timestamp DESC")
    suspend fun getAllDiscoveryCached(): List<DiscoveryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiscovery(entity: DiscoveryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllDiscovery(entities: List<DiscoveryEntity>)

    @Query("DELETE FROM discovery_cache WHERE timestamp < :threshold")
    suspend fun deleteOldDiscovery(threshold: Long)
    
    @Query("SELECT * FROM discovery_cache ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomDiscovery(): DiscoveryEntity?

    @Query("SELECT * FROM discovery_cache WHERE id = :id LIMIT 1")
    suspend fun getDiscoveryById(id: Long): DiscoveryEntity?

    @Query("DELETE FROM discovery_cache")
    suspend fun clearVault()
}
