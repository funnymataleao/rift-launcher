// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rift.app.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rift.library.LibraryRepository
import dev.rift.model.Game
import dev.rift.providers.LaunchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val games: List<Game> = emptyList(),
    val isRefreshing: Boolean = false,
    /** Outcome of the last Play press when it did not start the game. */
    val launchProblem: LaunchResult? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val library: LibraryRepository,
) : ViewModel() {

    private val isRefreshing = MutableStateFlow(false)
    private val launchProblem = MutableStateFlow<LaunchResult?>(null)

    val uiState: StateFlow<HomeUiState> =
        combine(library.games, isRefreshing, launchProblem, ::HomeUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refresh()
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                library.refresh()
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun play(game: Game) {
        viewModelScope.launch {
            val result = library.launch(game)
            launchProblem.value = result.takeUnless { it == LaunchResult.Started }
        }
    }
}
