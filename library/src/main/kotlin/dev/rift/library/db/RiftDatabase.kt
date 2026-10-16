// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rift.library.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [GameEntity::class, LaunchTargetEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class RiftDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
}
