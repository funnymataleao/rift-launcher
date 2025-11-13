package app.gamenative.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity to cache DepotManifest objects per depot.
 * Primary key is a combination of depotId and manifestGid as a string.
 */
@Entity("cached_depot_manifest", primaryKeys = ["depot_id", "manifest_gid"])
data class CachedDepotManifest(
    @ColumnInfo("depot_id")
    val depotId: Int,
    
    @ColumnInfo("manifest_gid")
    val manifestGid: Long,
    
    @ColumnInfo("manifest_json")
    val manifestJson: String, // Serialized DepotManifest object as JSON
    
    @ColumnInfo("cached_at")
    val cachedAt: Long = System.currentTimeMillis(), // Timestamp for cache invalidation
)

