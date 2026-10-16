// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rift.library.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import dev.rift.library.db.LibraryDao
import dev.rift.library.db.RiftDatabase
import dev.rift.providers.GameProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryModule {

    /** Declares the provider set so the app builds even when no provider module is included. */
    @Multibinds
    abstract fun gameProviders(): Set<GameProvider>

    companion object {
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): RiftDatabase =
            Room.databaseBuilder(context, RiftDatabase::class.java, "rift.db").build()

        @Provides
        fun libraryDao(database: RiftDatabase): LibraryDao = database.libraryDao()
    }
}
