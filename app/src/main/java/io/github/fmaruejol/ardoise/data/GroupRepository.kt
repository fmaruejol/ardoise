package io.github.fmaruejol.ardoise.data

import io.github.fmaruejol.ardoise.api.GroupInput
import io.github.fmaruejol.ardoise.api.SpliitApi
import io.github.fmaruejol.ardoise.core.model.Group
import io.github.fmaruejol.ardoise.core.model.GroupDetails
import io.github.fmaruejol.ardoise.core.model.GroupSummary
import io.github.fmaruejol.ardoise.core.prefs.GroupPreferences
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.data.local.SpliitCache
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/**
 * Groups, and the local record of which ones the user knows.
 *
 * Reads are `Flow`s from the cache first and refreshed behind it; mutations
 * are `suspend`. The **ids** are not cached data. They live in DataStore,
 * they are the only credential Spliit has, and losing one loses the group.
 */
class GroupRepository(
    private val api: SpliitApi,
    private val preferences: GroupPreferences,
    private val cache: SpliitCache,
    private val outbox: ExpenseOutbox,
) {
    /**
     * The groups the user knows, most recently opened first. Driven by the
     * stored ids, so remembering or forgetting one re-emits on its own.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun groups(): Flow<SpliitResult<List<GroupSummary>>> =
        preferences.knownGroupIds.flatMapLatest { groupIds ->
            if (groupIds.isEmpty()) {
                // Asking about no groups is a wasted round trip.
                flow { emit(SpliitResult.Success(emptyList())) }
            } else {
                cache.cachedThenFresh(
                    local = cache.groups(groupIds),
                    // Ids are known, so empty means "not fetched yet".
                    isCached = { it.isNotEmpty() },
                    refresh = { cache.refreshGroups(groupIds) },
                )
            }
        }

    /** One group, or null when no group has this id, a state, not an error. */
    fun group(groupId: String): Flow<SpliitResult<Group?>> =
        cache.cachedThenFresh(
            local = cache.group(groupId),
            // Null is "never fetched" until the refresh has run.
            isCached = { it != null },
            refresh = { cache.refreshGroup(groupId) },
        )

    /**
     * As [group], plus which participants already appear on an expense. Not
     * cached: asked once, on a screen that cannot save offline anyway.
     */
    fun groupDetails(groupId: String): Flow<SpliitResult<GroupDetails>> =
        flow { emit(api.getGroupDetails(groupId)) }

    /** Which participant the user says they are in this group, if they picked one. */
    fun activeParticipantId(groupId: String): Flow<String?> =
        preferences.activeParticipantId(groupId)

    /**
     * The currency the create-group form opens on. Here because it is about
     * creating a group, beside the other device-local answer about groups. It
     * changes nothing about a group that exists.
     */
    val defaultCurrencyCode: Flow<String> = preferences.defaultCurrencyCode

    suspend fun setDefaultCurrencyCode(code: String) {
        preferences.setDefaultCurrencyCode(code)
    }

    suspend fun setActiveParticipant(groupId: String, participantId: String?) {
        preferences.setActiveParticipantId(groupId, participantId)
    }

    /**
     * Creates a group and remembers its id, one operation, because the id is
     * the access to the group and a create that loses it strands the user.
     */
    suspend fun create(group: GroupInput): SpliitResult<String> {
        val result = api.createGroup(group)
        if (result is SpliitResult.Success) {
            preferences.rememberGroup(result.value)
            cache.refreshGroup(result.value)
        }
        return result
    }

    suspend fun update(groupId: String, group: GroupInput): SpliitResult<Unit> {
        val result = api.updateGroup(
            groupId = groupId,
            group = group,
            participantId = preferences.activeParticipantId(groupId).first(),
        )
        if (result is SpliitResult.Success) {
            // Fetching it back is what updates every screen: the group row and
            // its participants are what they all read names from.
            cache.refreshGroup(groupId)
        }
        return result
    }

    /**
     * Looks a group up once, without observing it: an id that does not resolve
     * is absent from [groups], so a pasted link has to be checked first. Goes
     * to the server, because that is the whole question.
     */
    suspend fun findGroup(groupId: String): SpliitResult<Group?> = api.getGroup(groupId)

    /** The ids saved against a particular server, current or not. */
    fun groupIdsFor(baseUrl: String): Flow<List<String>> = preferences.knownGroupIds(baseUrl)

    /** The ids saved for the current server, without asking the server about them. */
    val knownGroupIds: Flow<List<String>> = preferences.knownGroupIds

    /** Records a group the user reached by link, so it survives the next launch. */
    suspend fun remember(groupId: String) {
        preferences.rememberGroup(groupId)
    }

    /**
     * Removes the group from this device only, cached contents and all. It is
     * not deleted for anyone else, and without the id there is no way back in.
     */
    suspend fun forget(groupId: String) {
        preferences.forgetGroup(groupId)
        cache.forget(groupId)
        // Anything still queued for it would wait for ever.
        outbox.discardAllFor(groupId)
    }
}
