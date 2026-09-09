package io.github.fmaruejol.ardoise.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.fmaruejol.ardoise.data.AppLanguage
import io.github.fmaruejol.ardoise.data.AppTheme
import io.github.fmaruejol.ardoise.data.Connectivity
import io.github.fmaruejol.ardoise.data.ExpenseRepository
import io.github.fmaruejol.ardoise.data.GroupRepository
import io.github.fmaruejol.ardoise.data.LanguageRepository
import io.github.fmaruejol.ardoise.data.ServerRepository
import io.github.fmaruejol.ardoise.data.ThemeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Decides where the app opens: the first-run screen unless a server was chosen
 * or a group is already saved. The stored address cannot answer it alone,
 * since its default *is* the cloud.
 *
 * It also owns the outbox's one background job.
 */
class AppViewModel(
    serverRepository: ServerRepository,
    groupRepository: GroupRepository,
    languageRepository: LanguageRepository,
    themeRepository: ThemeRepository,
    private val expenses: ExpenseRepository,
    connectivity: Connectivity,
) : ViewModel() {
    init {
        // The queue is the app's: an expense queued in one group should not
        // wait for that group to be opened again.
        viewModelScope.launch {
            connectivity.available().collect { expenses.sendQueued() }
        }
    }

    /**
     * The language to draw in, null until read. Observed rather than read once:
     * choosing one changes what is on screen now. The app draws nothing until
     * it has an answer, so a French phone shows no English frame first.
     */
    val language: StateFlow<AppLanguage?> = languageRepository.language
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The theme to draw in, null until read. `MainActivity` reads it. */
    val theme: StateFlow<AppTheme?> = themeRepository.theme
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Null until it is known, which is a frame or two on a cold start. */
    val startDestination: StateFlow<String?> = flow {
        val chosenServer = serverRepository.hasChosenServer.first()
        val hasGroups = groupRepository.knownGroupIds.first().isNotEmpty()
        emit(if (chosenServer || hasGroups) Routes.GROUPS else Routes.HOME)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
