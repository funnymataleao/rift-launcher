// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rift.providers.pcstream

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.rift.providers.GameProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class PcStreamProviderModule {
    @Binds
    @IntoSet
    abstract fun bind(provider: PcStreamProvider): GameProvider
}
