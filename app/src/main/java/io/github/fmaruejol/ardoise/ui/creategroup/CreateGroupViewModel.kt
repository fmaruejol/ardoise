package io.github.fmaruejol.ardoise.ui.creategroup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.fmaruejol.ardoise.api.GroupInput
import io.github.fmaruejol.ardoise.api.ParticipantInput
import io.github.fmaruejol.ardoise.core.currency.GroupCurrency
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.data.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What is wrong with the form. Each of these but [Failed] names the row it
 * belongs to, so the field can be marked and the reason put under it.
 */
sealed interface CreateGroupError {
    data object Name : CreateGroupError

    data object You : CreateGroupError

    /** The participant at [index] has a name the server would refuse. */
    data class ParticipantName(val index: Int) : CreateGroupError

    /** The participant at [index] repeats a name already in the list. */
    data class DuplicateParticipant(val index: Int) : CreateGroupError

    /** The server refused the group. Belongs to no field. */
    data class Failed(val error: SpliitError) : CreateGroupError

    /** Which participant row this is about, or null when it belongs to the form. */
    val participantIndex: Int?
        get() = when (this) {
            is ParticipantName -> index
            is DuplicateParticipant -> index
            else -> null
        }

    /**
     * Where the field sits on the form, top first, the name, the participants
     * in their own order, then which one is you. A set has no order.
     */
    val fieldOrder: Int
        get() = when (this) {
            Name -> 0
            is ParticipantName -> PARTICIPANTS + index
            is DuplicateParticipant -> PARTICIPANTS + index
            You -> Int.MAX_VALUE - 1
            is Failed -> Int.MAX_VALUE
        }

    private companion object {
        /** Participants sit below the name and above everything after them. */
        const val PARTICIPANTS = 1
    }
}

data class CreateGroupUiState(
    val name: String = "",
    /** Stands in only for the moment before the stored preference arrives. */
    val currencyCode: String = GroupCurrency.defaultCodeFor(),
    /** Free text shown to everyone in the group. Optional. */
    val information: String = "",
    /** Names only. The server assigns the ids. */
    val participants: List<String> = listOf(""),
    /**
     * Which participant the person creating the group is, as an index into
     * [participants], since they have no ids until the server assigns them.
     *
     * Kept on this device only: Spliit has no accounts, so there is nobody to
     * tell. It is what preselects "Paid by" and what makes a balance "yours".
     */
    val youIndex: Int = 0,
    /** Whether the "which one is you" picker is open. */
    val pickingYou: Boolean = false,
    val isSaving: Boolean = false,
    /** Everything wrong with the form, one entry per bad row. */
    val errors: Set<CreateGroupError> = emptySet(),
    /** Refused saves, so the screen knows to scroll again. See the expense form. */
    val refusedSaves: Int = 0,
    /**
     * Rows the user has added, so the screen can focus the new one. A count
     * rather than an index: two blank rows look exactly like one.
     */
    val participantsAdded: Int = 0,
    /** Set once the group exists; consumed by the screen to navigate into it. */
    val createdGroupId: String? = null,
) {
    val canSave: Boolean get() = !isSaving

    /** The topmost field that is wrong, or null when nothing is. */
    val firstError: CreateGroupError?
        get() = errors.filterNot { it is CreateGroupError.Failed }.minByOrNull { it.fieldOrder }

    /** The one complaint about [field], or null when there is none. */
    fun errorOf(field: CreateGroupError): CreateGroupError? = field.takeIf { it in errors }

    /** The name of the participant marked as the user, blank until typed. */
    val youName: String get() = participants.getOrNull(youIndex).orEmpty()
}

/**
 * Creating a group. The same rules the server's schema enforces are checked
 * here, where they can be said in words rather than coming back as a
 * `BAD_REQUEST`.
 */
class CreateGroupViewModel(
    private val repository: GroupRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val restored: CreateGroupDraft? = savedState[DRAFT]

    private val _state = MutableStateFlow(
        restored?.let { CreateGroupUiState().withDraft(it) } ?: CreateGroupUiState(),
    )
    val state: StateFlow<CreateGroupUiState> = _state.asStateFlow()

    /**
     * True once the user has touched the currency picker: the stored default
     * arrives asynchronously and must not overwrite a choice already made.
     */
    private var currencyChosen = restored?.currencyChosen ?: false

    init {
        viewModelScope.launch {
            val code = repository.defaultCurrencyCode.first()
            if (!currencyChosen) _state.update { it.copy(currencyCode = code) }
        }
        viewModelScope.launch {
            _state.collect { savedState[DRAFT] = it.draft(currencyChosen) }
        }
    }

    fun onNameChange(name: String) {
        _state.update { it.copy(name = name, errors = it.errors - CreateGroupError.Name) }
    }

    fun onCurrencyChange(code: String) {
        currencyChosen = true
        _state.update { it.copy(currencyCode = code) }
    }

    fun onInformationChange(information: String) {
        _state.update { it.copy(information = information) }
    }

    fun onPickYouOpen() {
        _state.update { it.copy(pickingYou = true) }
    }

    fun onPickYouDismiss() {
        _state.update { it.copy(pickingYou = false) }
    }

    fun onYouChange(index: Int) {
        _state.update {
            it.copy(
                youIndex = index,
                pickingYou = false,
                errors = it.errors - CreateGroupError.You,
            )
        }
    }

    fun onParticipantChange(index: Int, name: String) {
        _state.update { state ->
            state.copy(
                participants = state.participants.toMutableList().also { it[index] = name },
                // This row's complaint only: a duplicate is about two rows, and
                // the other stays marked until the next save agrees.
                errors = state.errors.filterNot { it.participantIndex == index }.toSet(),
            )
        }
    }

    fun onAddParticipant() {
        // A new row at the end shifts nothing, so the marks above it stand.
        _state.update {
            it.copy(
                participants = it.participants + "",
                participantsAdded = it.participantsAdded + 1,
            )
        }
    }

    fun onRemoveParticipant(index: Int) {
        _state.update { state ->
            // The first row is the user themselves, and a group needs at least
            // one participant, so it is not removable.
            if (index == 0 || state.participants.size <= 1) return@update state
            state.copy(
                participants = state.participants.filterIndexed { i, _ -> i != index },
                // The selection is an index, so removing a row above it moves
                // it; removing the selected row falls back to the first.
                youIndex = when {
                    index == state.youIndex -> 0
                    index < state.youIndex -> state.youIndex - 1
                    else -> state.youIndex
                },
                // Removing a row moves every row under it, so a mark by index
                // would land on somebody else. Worked out again on the next save.
                errors = state.errors.filter { it.participantIndex == null }.toSet(),
            )
        }
    }

    fun onSave() {
        val current = _state.value
        if (current.isSaving) return

        // Every rule at once, so the user sees everything that is wrong.
        val errors = mutableSetOf<CreateGroupError>()

        val name = current.name.trim()
        if (name.length !in NAME_RANGE) errors += CreateGroupError.Name

        // Indexed against the rows as typed: blank rows are dropped before
        // saving, so a position in the shorter list marks the wrong name.
        val named = current.participants
            .mapIndexed { index, name -> index to name.trim() }
            .filter { (_, name) -> name.isNotEmpty() }
        val participants = named.map { (_, name) -> name }

        if (named.isEmpty()) {
            // Nothing typed anywhere; the first row is where it would go.
            errors += CreateGroupError.ParticipantName(0)
        }
        named.filter { (_, name) -> name.length !in NAME_RANGE }.forEach { (index, _) ->
            errors += CreateGroupError.ParticipantName(index)
        }
        val seen = mutableSetOf<String>()
        named.filter { (_, name) -> !seen.add(name.lowercase()) }.forEach { (index, _) ->
            // The second of each pair, which the user just typed.
            errors += CreateGroupError.DuplicateParticipant(index)
        }

        // Blank rows are dropped above, so the selection resolves by name.
        val youName = current.youName.trim()
        if (youName !in participants) errors += CreateGroupError.You

        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors, refusedSaves = it.refusedSaves + 1) }
            return
        }

        _state.update { it.copy(isSaving = true, errors = emptySet()) }
        viewModelScope.launch {
            val input = GroupInput(
                name = name,
                currencySymbol = symbolFor(current.currencyCode),
                currencyCode = current.currencyCode,
                participants = participants.map { ParticipantInput(name = it) },
                information = current.information.trim().takeIf { it.isNotEmpty() },
            )
            when (val result = repository.create(input)) {
                is SpliitResult.Success -> {
                    // What makes "you owe / you are owed" mean anything later.
                    claimParticipant(result.value, youName)
                    _state.update { it.copy(isSaving = false, createdGroupId = result.value) }
                }

                is SpliitResult.Failure -> {
                    _state.update {
                        it.copy(
                            isSaving = false,
                            errors = setOf(CreateGroupError.Failed(result.error)),
                        )
                    }
                }
            }
        }
    }

    fun onNavigationHandled() {
        _state.update { it.copy(createdGroupId = null) }
    }

    /**
     * `groups.create` returns only the group id, so the participants are read
     * back before the chosen one can be marked. Best effort: failing costs a
     * tap inside the group rather than the group.
     */
    private suspend fun claimParticipant(groupId: String, name: String) {
        val group = (repository.group(groupId).first() as? SpliitResult.Success)?.value ?: return
        val me = group.participants.firstOrNull { it.name == name } ?: return
        repository.setActiveParticipant(groupId, me.id)
    }

    private fun CreateGroupUiState.draft(currencyChosen: Boolean) = CreateGroupDraft(
        name = name,
        currencyCode = currencyCode,
        currencyChosen = currencyChosen,
        information = information,
        participants = participants,
        youIndex = youIndex,
    )

    private fun CreateGroupUiState.withDraft(draft: CreateGroupDraft) = copy(
        name = draft.name,
        currencyCode = draft.currencyCode,
        information = draft.information,
        participants = draft.participants.ifEmpty { listOf("") },
        youIndex = draft.youIndex.coerceIn(0, (draft.participants.size - 1).coerceAtLeast(0)),
    )

    private fun symbolFor(code: String): String =
        runCatching { java.util.Currency.getInstance(code).symbol }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it.length <= MAX_SYMBOL }
            ?: code

    private companion object {
        val NAME_RANGE = 2..50
        const val MAX_SYMBOL = 5

        const val DRAFT = "create-group-draft"
    }
}
