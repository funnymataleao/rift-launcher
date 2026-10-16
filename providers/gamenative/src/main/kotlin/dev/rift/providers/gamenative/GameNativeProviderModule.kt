// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rift.providers.gamenative

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.rift.providers.GameProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class GameNativeProviderModule {
    @Binds
    @IntoSet
    abstract fun bind(provider: GameNativeProvider): GameProvider
}
