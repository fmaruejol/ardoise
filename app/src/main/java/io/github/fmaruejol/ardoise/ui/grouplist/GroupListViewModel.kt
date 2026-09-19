package io.github.fmaruejol.ardoise.ui.grouplist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.fmaruejol.ardoise.core.model.GroupSummary
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.data.BalanceRepository
import io.github.fmaruejol.ardoise.data.Connectivity
import io.github.fmaruejol.ardoise.data.GroupRepository
import io.github.fmaruejol.ardoise.ui.Retry
import io.github.fmaruejol.ardoise.ui.collectOffline
import io.github.fmaruejol.ardoise.ui.collectRefreshing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupListUiState(
    /** The first load, before anything can be shown. */
    val isLoading: Boolean = true,
    val groups: List<GroupSummary> = emptyList(),
    /** A failed load, kept beside [groups]: it should not blank the list. */
    val error: SpliitError? = null,
    val isRefreshing: Boolean = false,
    /**
     * Where the user stands in each group, in its own minor units. **Absent
     * rather than zero when there is nothing to say**, because nobody has
     * said who they are, or the server could not be asked. Zero means settled.
     */
    val positions: Map<String, Long> = emptyMap(),
    /** Read only to raise the offline banner over groups from the cache. */
    val isOffline: Boolean = false,
    /** Whether the search field has replaced the title. */
    val isSearching: Boolean = false,
    val query: String = "",
) {
    /** The groups actually listed, once the search has had its say. */
    val visibleGroups: List<GroupSummary>
        get() = if (query.isBlank()) {
            groups
        } else {
            groups.filter { it.name.contains(query.trim(), ignoreCase = true) }
        }

    /** Nothing has ever been added, as opposed to nothing matching a search. */
    val isEmpty: Boolean get() = !isLoading && groups.isEmpty() && error == null

    /** Groups exist, but none of them match what was typed. */
    val hasNoMatches: Boolean
        get() = !isLoading && groups.isNotEmpty() && visibleGroups.isEmpty()
}

/**
 * The groups this device knows about. Spliit has no accounts and no server-side
 * search, so a group is reachable only through its link: this list is the
 * whole of the app's navigation, and losing an entry loses the group.
 */
class GroupListViewModel(
    private val repository: GroupRepository,
    private val balances: BalanceRepository,
    private val connectivity: Connectivity,
) : ViewModel() {
    private val _state = MutableStateFlow(GroupListUiState())
    val state: StateFlow<GroupListUiState> = _state.asStateFlow()

    private val retry = Retry()

    init {
        collectOffline(connectivity, _state) { copy(isOffline = it) }
        collectRefreshing(retry, _state) { copy(isRefreshing = it) }
    }

    init {
        viewModelScope.launch {
            retry.restarting { repository.groups() }.collect { result ->
                _state.update {
                    when (result) {
                        is SpliitResult.Success -> it.copy(
                            isLoading = false,
                            groups = result.value,
                            error = null,
                        )

                        is SpliitResult.Failure -> it.copy(
                            isLoading = false,
                            error = result.error,
                        )
                    }
                }
            }
        }
    }

    init {
        // Its own read: `groups.list` returns no balance, and this one call
        // covers every group at once.
        viewModelScope.launch {
            retry.restarting { balances.userBalances() }.collect { result ->
                // A failure leaves the last answer standing: the line is an
                // extra on a card that reads fine without it, and it is not
                // cached, so offline is a missing line rather than an error.
                val positions = (result as? SpliitResult.Success)?.value ?: return@collect
                _state.update {
                    it.copy(positions = positions.associate { row -> row.groupId to row.amount })
                }
            }
        }
    }

    fun onRefresh() = retry.again()

    // --- searching ---------------------------------------------------------

    fun onSearchOpen() {
        _state.update { it.copy(isSearching = true) }
    }

    fun onSearchClose() {
        _state.update { it.copy(isSearching = false, query = "") }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }
}
