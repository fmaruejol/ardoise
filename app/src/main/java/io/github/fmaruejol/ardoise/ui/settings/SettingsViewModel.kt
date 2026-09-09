package io.github.fmaruejol.ardoise.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.fmaruejol.ardoise.core.currency.GroupCurrency
import io.github.fmaruejol.ardoise.core.prefs.GroupPreferences
import io.github.fmaruejol.ardoise.data.AppLanguage
import io.github.fmaruejol.ardoise.data.AppTheme
import io.github.fmaruejol.ardoise.data.GroupRepository
import io.github.fmaruejol.ardoise.data.LanguageRepository
import io.github.fmaruejol.ardoise.data.ServerRepository
import io.github.fmaruejol.ardoise.data.ThemeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    /** Always with a trailing slash; the screen shows only the host. */
    val baseUrl: String = GroupPreferences.DEFAULT_BASE_URL,
    /** ISO code the create-group form will open on. */
    val defaultCurrencyCode: String = GroupCurrency.defaultCodeFor(),
    /** Which language the app is read in, or [AppLanguage.System]. */
    val language: AppLanguage = AppLanguage.System,
    /** Which scheme the app is drawn in, or [AppTheme.System]. */
    val theme: AppTheme = AppTheme.System,
)

/**
 * App settings. Every row is observed rather than read once, so coming
 * back from another screen shows the new value without a reload.
 */
class SettingsViewModel(
    serverRepository: ServerRepository,
    private val groups: GroupRepository,
    private val languages: LanguageRepository,
    private val themes: ThemeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            serverRepository.baseUrl.collect { url -> _state.update { it.copy(baseUrl = url) } }
        }
        viewModelScope.launch {
            groups.defaultCurrencyCode.collect { code ->
                _state.update { it.copy(defaultCurrencyCode = code) }
            }
        }
        viewModelScope.launch {
            languages.language.collect { language ->
                _state.update { it.copy(language = language) }
            }
        }
        viewModelScope.launch {
            themes.theme.collect { theme -> _state.update { it.copy(theme = theme) } }
        }
    }

    /** Saved the moment it is chosen: it cannot fail and cannot be rolled back. */
    fun onDefaultCurrencyChange(code: String) {
        viewModelScope.launch { groups.setDefaultCurrencyCode(code) }
    }

    /** Saved the same way; `AppViewModel` observes it, so there is no restart. */
    fun onLanguageChange(language: AppLanguage) {
        viewModelScope.launch { languages.setLanguage(language) }
    }

    /** Saved the same way, and the app is redrawn in it at once. */
    fun onThemeChange(theme: AppTheme) {
        viewModelScope.launch { themes.setTheme(theme) }
    }
}
