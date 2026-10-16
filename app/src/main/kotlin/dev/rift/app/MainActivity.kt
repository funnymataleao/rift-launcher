// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rift.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.rift.app.home.HomeScreen
import dev.rift.app.home.HomeViewModel
import dev.rift.ui.theme.RiftTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RiftTheme {
                val state by homeViewModel.uiState.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    onPlay = homeViewModel::play,
                    onRefresh = homeViewModel::refresh,
                )
            }
        }
    }
}
