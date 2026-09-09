package io.github.fmaruejol.ardoise.ui.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.fmaruejol.ardoise.core.instance.GroupLink
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.data.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface JoinError {
    /** Nothing in the text looked like a group link or id. */
    data object NotALink : JoinError

    /** The link parsed, but this server has no such group. */
    data object NotFound : JoinError

    data class Failed(val error: SpliitError) : JoinError
}

data class JoinGroupUiState(
    val link: String = "",
    val isJoining: Boolean = false,
    val error: JoinError? = null,
    /** Set once the group is saved; consumed by the screen to navigate. */
    val joinedGroupId: String? = null,
    /**
     * The scanner with the whole screen, swapped in for this body. A
     * state rather than a destination: it fills in the link this screen wants.
     */
    val scanningFullScreen: Boolean = false,
) {
    val canJoin: Boolean get() = link.isNotBlank() && !isJoining
}

/**
 * Joining a group by its link or QR code, the only way in, since Spliit has
 * no accounts and no search. The link is checked before it is saved: an id
 * that does not resolve would simply be absent from the list, as if the app
 * had swallowed it.
 */
class JoinGroupViewModel(private val repository: GroupRepository) : ViewModel() {
    private val _state = MutableStateFlow(JoinGroupUiState())
    val state: StateFlow<JoinGroupUiState> = _state.asStateFlow()

    fun onLinkChange(link: String) {
        _state.update { it.copy(link = link, error = null) }
    }

    /**
     * A QR code was read. Scanning is already an explicit "add this group", so
     * it goes straight through. The text still goes into the field first, so a
     * code that is not a Spliit link can be seen rather than only reported.
     */
    fun onScanned(scanned: String) {
        if (_state.value.isJoining) return
        // Closing the scanner is part of reading a code: leaving the camera up
        // would invite a second scan of the same one.
        _state.update { it.copy(link = scanned, error = null, scanningFullScreen = false) }
        onJoin()
    }

    fun onFullScreenOpen() = _state.update { it.copy(scanningFullScreen = true) }

    fun onFullScreenClose() = _state.update { it.copy(scanningFullScreen = false) }

    fun onJoin() {
        val current = _state.value
        if (current.isJoining) return

        val groupId = GroupLink.parse(current.link)
        if (groupId == null) {
            _state.update { it.copy(error = JoinError.NotALink) }
            return
        }

        _state.update { it.copy(isJoining = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.findGroup(groupId)) {
                is SpliitResult.Success -> {
                    if (result.value == null) {
                        _state.update { it.copy(isJoining = false, error = JoinError.NotFound) }
                    } else {
                        repository.remember(groupId)
                        _state.update { it.copy(isJoining = false, joinedGroupId = groupId) }
                    }
                }

                is SpliitResult.Failure -> {
                    _state.update {
                        it.copy(isJoining = false, error = JoinError.Failed(result.error))
                    }
                }
            }
        }
    }

    fun onNavigationHandled() {
        _state.update { it.copy(joinedGroupId = null) }
    }
}
