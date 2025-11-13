package app.gamenative.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.gamenative.data.CachedDepotManifest

@Dao
interface DepotManifestDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(manifest: CachedDepotManifest)
    
    @Query("SELECT * FROM cached_depot_manifest WHERE depot_id = :depotId AND manifest_gid = :manifestGid")
    suspend fun getManifest(depotId: Int, manifestGid: Long): CachedDepotManifest?
    
    @Query("DELETE FROM cached_depot_manifest WHERE depot_id = :depotId AND manifest_gid = :manifestGid")
    suspend fun deleteManifest(depotId: Int, manifestGid: Long)
    
    @Query("DELETE FROM cached_depot_manifest WHERE cached_at < :beforeTimestamp")
    suspend fun deleteOldManifests(beforeTimestamp: Long)
    
    @Query("DELETE FROM cached_depot_manifest")
    suspend fun deleteAll()
}

