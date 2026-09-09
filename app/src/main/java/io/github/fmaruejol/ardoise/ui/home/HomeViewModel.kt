package io.github.fmaruejol.ardoise.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.fmaruejol.ardoise.core.prefs.GroupPreferences
import io.github.fmaruejol.ardoise.data.ServerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    /** Always with a trailing slash; the screen shows only the host. */
    val baseUrl: String = GroupPreferences.DEFAULT_BASE_URL,
)

/**
 * The first-run screen. Its only state is which server the app is pointed at,
 * observed so coming back from the server screen shows the new address
 * without a reload.
 */
class HomeViewModel(serverRepository: ServerRepository) : ViewModel() {
    val state: StateFlow<HomeUiState> = serverRepository.baseUrl
        .map { HomeUiState(baseUrl = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
