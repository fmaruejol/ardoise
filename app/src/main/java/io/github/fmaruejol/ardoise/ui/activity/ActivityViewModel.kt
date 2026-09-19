package io.github.fmaruejol.ardoise.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.fmaruejol.ardoise.core.model.Activity
import io.github.fmaruejol.ardoise.core.model.ActivityType
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.data.ActivityRepository
import io.github.fmaruejol.ardoise.data.Connectivity
import io.github.fmaruejol.ardoise.data.GroupRepository
import io.github.fmaruejol.ardoise.ui.Retry
import io.github.fmaruejol.ardoise.ui.collectOffline
import io.github.fmaruejol.ardoise.ui.collectRefreshing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One line of the log, with the names already resolved. */
data class ActivityRow(
    val id: String,
    val type: ActivityType,
    val time: Instant,
    /** Empty when the server recorded no participant, an anonymous edit. */
    val who: String,
    val byYou: Boolean,
    /** The expense's title, when the activity is about one. */
    val what: String?,
    val expenseId: String?,
)

/** A day's worth of activity. The header is resolved in the UI, not here. */
data class ActivityDay(
    val date: LocalDate,
    val rows: List<ActivityRow>,
)

data class ActivityUiState(
    val isLoading: Boolean = true,
    val days: List<ActivityDay> = emptyList(),
    val hasMore: Boolean = false,
    /** Read only to raise the offline banner over rows from the cache. */
    val isOffline: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: SpliitError? = null,
) {
    val isEmpty: Boolean get() = !isLoading && days.isEmpty() && error == null
}

/**
 * What has happened in a group. Read only: the server writes the log as a
 * side effect of every mutation, so this re-emits when an expense changes.
 *
 * "Show older" grows the page rather than fetching the next one, as the feed
 * does, so everything on screen is cached. Days are cut in the **device's**
 * time zone: the server stores an instant, the user reads "Today".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModel(
    private val groupId: String,
    activities: ActivityRepository,
    groups: GroupRepository,
    private val connectivity: Connectivity,
) : ViewModel() {
    private val _state = MutableStateFlow(ActivityUiState())
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()

    private val pageSize = MutableStateFlow(FIRST_PAGE)
    private val retry = Retry()

    init {
        collectOffline(connectivity, _state) { copy(isOffline = it) }
        collectRefreshing(retry, _state) { copy(isRefreshing = it) }
    }

    init {
        viewModelScope.launch {
            combine(pageSize, retry.restarts) { limit, scope -> limit to scope }
                .flatMapLatest { (limit, scope) ->
                    retry.track(
                        scope,
                        combine(
                            groups.group(groupId),
                            activities.activities(groupId, limit),
                            groups.activeParticipantId(groupId),
                        ) { group, result, active -> Triple(group, result, active) },
                    )
                }
                .collect { (groupResult, pageResult, active) ->
                    val names = (groupResult as? SpliitResult.Success)?.value
                        ?.participants.orEmpty()
                        .associate { it.id to it.name }

                    when (pageResult) {
                        is SpliitResult.Failure -> {
                            _state.update { it.copy(isLoading = false, error = pageResult.error) }
                        }

                        is SpliitResult.Success -> {
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    error = null,
                                    days = pageResult.value.activities.toDays(names, active),
                                    hasMore = pageResult.value.hasMore,
                                )
                            }
                        }
                    }
                }
        }
    }

    /**
     * More log, the way the feed does it: a longer page rather than the next
     * one. The cache stores a group's log as "the newest N, replaced", which
     * is what makes an entry that vanished upstream vanish here too.
     */
    fun onLoadMore() {
        if (!_state.value.hasMore) return
        pageSize.update { it + PAGE_STEP }
    }

    fun onRefresh() = retry.again()

    private fun List<Activity>.toDays(
        names: Map<String, String>,
        me: String?,
    ): List<ActivityDay> {
        val zone = ZoneId.systemDefault()
        return groupBy { it.time.atZone(zone).toLocalDate() }
            .map { (date, activities) ->
                ActivityDay(
                    date = date,
                    rows = activities.map { it.toRow(names, me) },
                )
            }
            // The server orders newest first; saying so here means the screen
            // does not depend on it.
            .sortedByDescending { it.date }
    }

    private fun Activity.toRow(names: Map<String, String>, me: String?): ActivityRow =
        ActivityRow(
            id = id,
            type = activityType,
            time = time,
            who = participantId?.let { names[it] }.orEmpty(),
            byYou = participantId != null && participantId == me,
            // The payload is the expense title; a blank one is treated as
            // absent so the UI can say "an expense".
            what = data?.takeIf { it.isNotBlank() },
            expenseId = expenseId,
        )

    private companion object {
        /** The same shape as the feed: a screenful, then another. */
        const val FIRST_PAGE = 30
        const val PAGE_STEP = 30
    }
}
