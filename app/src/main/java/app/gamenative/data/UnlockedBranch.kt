package app.gamenative.data

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "unlocked_branch",
    primaryKeys = ["appId", "branchName"],
)
data class UnlockedBranch(
    val appId: Int,
    @ColumnInfo("branchName")
    val branchName: String,
    @ColumnInfo("password")
    val password: String,
)
