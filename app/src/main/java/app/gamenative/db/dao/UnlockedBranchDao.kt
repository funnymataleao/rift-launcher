package app.gamenative.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.gamenative.data.UnlockedBranch

@Dao
interface UnlockedBranchDao {
    @Query("SELECT * FROM unlocked_branch WHERE appId = :appId")
    suspend fun getUnlockedBranches(appId: Int): List<UnlockedBranch>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(branch: UnlockedBranch)

    @Query("DELETE FROM unlocked_branch WHERE appId = :appId AND branchName = :branchName")
    suspend fun delete(appId: Int, branchName: String)

    @Query("DELETE FROM unlocked_branch")
    suspend fun deleteAll()
}
