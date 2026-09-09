package io.github.fmaruejol.ardoise.ui.groupsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.fmaruejol.ardoise.api.GroupInput
import io.github.fmaruejol.ardoise.api.ParticipantInput
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.data.GroupRepository
import io.github.fmaruejol.ardoise.data.ServerRepository
import io.github.fmaruejol.ardoise.ui.Retry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A participant as edited on this screen. A null [id] has not been created yet. */
data class ParticipantEdit(val id: String?, val name: String)

sealed interface GroupSettingsError {
    data object Name : GroupSettingsError

    /** The participant at [index] has a name the server would refuse. */
    data class ParticipantName(val index: Int) : GroupSettingsError

    /** The participant at [index] repeats a name already in the list. */
    data class DuplicateParticipant(val index: Int) : GroupSettingsError

    data class Failed(val error: SpliitError) : GroupSettingsError

    /** Which participant row this is about, or null when it belongs to the form. */
    val participantIndex: Int?
        get() = when (this) {
            is ParticipantName -> index
            is DuplicateParticipant -> index
            else -> null
        }

    /** Where the field this is about sits on the form, top first. */
    val fieldOrder: Int
        get() = when (this) {
            Name -> 0
            is ParticipantName -> 1 + index
            is DuplicateParticipant -> 1 + index
            is Failed -> Int.MAX_VALUE
        }
}

data class GroupSettingsUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val information: String = "",
    val participants: List<ParticipantEdit> = emptyList(),
    /**
     * Carried through untouched: `groups.update` takes the whole group, and the
     * ISO code decides the scale of every amount in it.
     */
    val currencySymbol: String = "",
    val currencyCode: String? = null,
    /** Participants on at least one expense: removing one is refused. */
    val participantIdsWithExpenses: Set<String> = emptySet(),
    /** Which participant this device says the user is. Never sent anywhere. */
    val activeParticipantId: String? = null,
    val inviteUrl: String = "",
    val isSaving: Boolean = false,
    /**
     * True once the group has been read, what the fields are seeded from, and
     * only once, so a re-emission cannot overwrite what is typed. `isLoading`
     * stood in for it until a retry, which arrives with it already false.
     */
    val isLoaded: Boolean = false,
    /** A load that failed. The screen has nothing to show when this is set. */
    val loadError: SpliitError? = null,
    /** The group id resolved to nothing, because it was deleted or never existed. */
    val notFound: Boolean = false,
    /** Everything wrong with the form, one entry per bad row. */
    val saveErrors: Set<GroupSettingsError> = emptySet(),
    /** Refused saves, so the screen knows to scroll again. See the expense form. */
    val refusedSaves: Int = 0,
    /** How many rows the user has added; see the create-group form. */
    val participantsAdded: Int = 0,
    val pickingYou: Boolean = false,
    /** Non-null while the user is being asked to confirm removing the group. */
    val confirmingRemoval: Boolean = false,
    /** A participant the user tried to remove who cannot be. */
    val blockedRemoval: String? = null,
    /** Set once the group is off this device; consumed by the screen. */
    val isRemoved: Boolean = false,
) {
    val activeParticipantName: String?
        get() = participants.firstOrNull { it.id != null && it.id == activeParticipantId }?.name

    val youIndex: Int
        get() = participants.indexOfFirst { it.id != null && it.id == activeParticipantId }

    /** The topmost field that is wrong, or null when nothing is. */
    val firstError: GroupSettingsError?
        get() = saveErrors
            .filterNot { it is GroupSettingsError.Failed }
            .minByOrNull { it.fieldOrder }

    /** The one complaint about [field], or null when there is none. */
    fun errorOf(field: GroupSettingsError): GroupSettingsError? = field.takeIf { it in saveErrors }

    val canSave: Boolean get() = !isSaving && !isLoading && loadError == null && !notFound
}

/**
 * Group settings. Two kinds of thing live here: the group itself, which
 * everyone shares and which goes to the server, and "which one is you", which
 * is this device's answer and is stored locally, and therefore saved the
 * moment it is made, since nothing about it can fail.
 */
class GroupSettingsViewModel(
    private val groupId: String,
    private val repository: GroupRepository,
    serverRepository: ServerRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(GroupSettingsUiState())
    val state: StateFlow<GroupSettingsUiState> = _state.asStateFlow()

    private val retry = Retry()

    init {
        viewModelScope.launch {
            val baseUrl = serverRepository.baseUrl.first()
            _state.update { it.copy(inviteUrl = "${baseUrl}groups/$groupId") }
        }
        viewModelScope.launch {
            // Restartable, because `groupDetails` is a one-shot uncached read:
            // a failure otherwise leaves no way back but leaving the group.
            retry.restarting {
                combine(
                    repository.groupDetails(groupId),
                    repository.activeParticipantId(groupId),
                ) { details, activeId -> details to activeId }
            }
                .collect { (details, activeId) ->
                    _state.update { current ->
                        when (details) {
                            is SpliitResult.Success -> current.copy(
                                isLoading = false,
                                isLoaded = true,
                                loadError = null,
                                notFound = false,
                                // Editing outranks a re-emission, which would
                                // otherwise throw away what is typed.
                                name = if (!current.isLoaded) {
                                    details.value.group.name
                                } else {
                                    current.name
                                },
                                information = if (!current.isLoaded) {
                                    details.value.group.information.orEmpty()
                                } else {
                                    current.information
                                },
                                participants = if (!current.isLoaded) {
                                    details.value.group.participants.map {
                                        ParticipantEdit(it.id, it.name)
                                    }
                                } else {
                                    current.participants
                                },
                                currencySymbol = details.value.group.currencySymbol,
                                currencyCode = details.value.group.currencyCode,
                                participantIdsWithExpenses =
                                    details.value.participantIdsWithExpenses.toSet(),
                                activeParticipantId = activeId,
                            )

                            is SpliitResult.Failure -> current.copy(
                                isLoading = false,
                                // `NOT_FOUND` here is the group being gone,
                                // which is a state to render.
                                notFound = details.error is SpliitError.NotFound,
                                loadError = details.error.takeUnless { it is SpliitError.NotFound },
                                activeParticipantId = activeId,
                            )
                        }
                    }
                }
        }
    }

    // --- the group, which everyone shares ----------------------------------

    fun onRetry() = retry.again()

    fun onNameChange(name: String) {
        _state.update { it.copy(name = name, saveErrors = it.saveErrors - GroupSettingsError.Name) }
    }

    fun onInformationChange(information: String) {
        _state.update { it.copy(information = information) }
    }

    fun onParticipantNameChange(index: Int, name: String) {
        _state.update { state ->
            state.copy(
                participants = state.participants.toMutableList().also {
                    it[index] = it[index].copy(name = name)
                },
                // This row's complaint only: a duplicate is about two rows, and
                // the other stays marked until the next save agrees.
                saveErrors = state.saveErrors.filterNot { it.participantIndex == index }.toSet(),
            )
        }
    }

    fun onAddParticipant() {
        _state.update {
            it.copy(
                participants = it.participants + ParticipantEdit(null, ""),
                participantsAdded = it.participantsAdded + 1,
            )
        }
    }

    fun onRemoveParticipant(index: Int) {
        val participant = _state.value.participants.getOrNull(index) ?: return
        if (participant.id != null && participant.id in _state.value.participantIdsWithExpenses) {
            // The server would refuse this, and rightly.
            _state.update { it.copy(blockedRemoval = participant.name) }
            return
        }
        _state.update { state ->
            state.copy(
                participants = state.participants.filterIndexed { i, _ -> i != index },
                // Removing a row moves every row under it, so a mark by index
                // would land on somebody else.
                saveErrors = state.saveErrors.filter { it.participantIndex == null }.toSet(),
            )
        }
    }

    fun onBlockedRemovalDismiss() {
        _state.update { it.copy(blockedRemoval = null) }
    }

    fun onSave() {
        val current = _state.value
        if (!current.canSave) return

        // Every rule at once, so the user sees everything that is wrong.
        val errors = mutableSetOf<GroupSettingsError>()

        val name = current.name.trim()
        if (name.length !in NAME_RANGE) errors += GroupSettingsError.Name

        // Indexed against the rows on screen, not the filtered list: blank rows
        // are dropped before saving, so a position there marks the wrong name.
        val named = current.participants
            .mapIndexed { index, participant -> index to participant.copy(name = participant.name.trim()) }
            .filter { (_, participant) -> participant.name.isNotEmpty() }
        val participants = named.map { (_, participant) -> participant }

        if (named.isEmpty()) {
            // Nothing typed anywhere; the first row is where it would go.
            errors += GroupSettingsError.ParticipantName(0)
        }
        named.filter { (_, it) -> it.name.length !in NAME_RANGE }.forEach { (index, _) ->
            errors += GroupSettingsError.ParticipantName(index)
        }
        val seen = mutableSetOf<String>()
        named.filter { (_, it) -> !seen.add(it.name.lowercase()) }.forEach { (index, _) ->
            errors += GroupSettingsError.DuplicateParticipant(index)
        }

        if (errors.isNotEmpty()) {
            _state.update { it.copy(saveErrors = errors, refusedSaves = it.refusedSaves + 1) }
            return
        }

        _state.update { it.copy(isSaving = true, saveErrors = emptySet()) }
        viewModelScope.launch {
            val input = GroupInput(
                name = name,
                currencySymbol = current.currencySymbol,
                currencyCode = current.currencyCode,
                participants = participants.map { ParticipantInput(name = it.name, id = it.id) },
                information = current.information.trim().takeIf { it.isNotEmpty() },
            )
            when (val result = repository.update(groupId, input)) {
                is SpliitResult.Success -> _state.update { it.copy(isSaving = false) }

                is SpliitResult.Failure -> _state.update {
                    it.copy(
                        isSaving = false,
                        saveErrors = setOf(GroupSettingsError.Failed(result.error)),
                    )
                }
            }
        }
    }

    // --- which one is you, which stays here --------------------------------

    fun onPickYouOpen() {
        _state.update { it.copy(pickingYou = true) }
    }

    fun onPickYouDismiss() {
        _state.update { it.copy(pickingYou = false) }
    }

    /** Records who the user is. Written straight away: it never leaves the device. */
    fun onYouChange(index: Int) {
        val participant = _state.value.participants.getOrNull(index)
        _state.update { it.copy(pickingYou = false) }
        // A participant never saved has no id to record.
        val id = participant?.id ?: return
        viewModelScope.launch { repository.setActiveParticipant(groupId, id) }
    }

    // --- removing the group from this device -------------------------------

    fun onRemoveGroupClick() {
        _state.update { it.copy(confirmingRemoval = true) }
    }

    fun onRemoveGroupDismiss() {
        _state.update { it.copy(confirmingRemoval = false) }
    }

    fun onRemoveGroupConfirm() {
        _state.update { it.copy(confirmingRemoval = false) }
        viewModelScope.launch {
            repository.forget(groupId)
            _state.update { it.copy(isRemoved = true) }
        }
    }

    fun onNavigationHandled() {
        _state.update { it.copy(isRemoved = false) }
    }

    private companion object {
        val NAME_RANGE = 2..50
    }
}
