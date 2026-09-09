package io.github.fmaruejol.ardoise.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.fmaruejol.ardoise.core.instance.BaseUrl
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.data.GroupRepository
import io.github.fmaruejol.ardoise.data.NetworkAccess
import io.github.fmaruejol.ardoise.data.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InstanceChoice { Cloud, SelfHosted }

/** What the last reachability test said, if one has been run. */
sealed interface CheckOutcome {
    data object Reachable : CheckOutcome

    data class Failed(val error: SpliitError) : CheckOutcome
}

data class ServerUiState(
    val choice: InstanceChoice = InstanceChoice.Cloud,
    val customUrl: String = "",
    /** Why the typed address is not usable, or null while it looks fine. */
    val urlError: BaseUrl.Reason? = null,
    val isChecking: Boolean = false,
    val checkOutcome: CheckOutcome? = null,
    /** The address currently in use, which is what the switch note talks about. */
    val savedBaseUrl: String = "",
    /** How many groups are saved against [savedBaseUrl]. */
    val savedGroupCount: Int = 0,
    /** Android is refusing the network. Nothing works until that stops. */
    val networkAccessBlocked: Boolean = false,
    /** The user closed the prompt. The requirement stands; the dialog does not. */
    val permissionPromptDismissed: Boolean = false,
    /** Set once saved; consumed by the screen so a rotation does not navigate twice. */
    val isSaved: Boolean = false,
) {
    /** The address the screen would save, or null while it cannot be read. */
    val pendingBaseUrl: String?
        get() = when (choice) {
            InstanceChoice.Cloud -> {
                BaseUrl.CLOUD
            }

            InstanceChoice.SelfHosted -> {
                (BaseUrl.normalise(customUrl) as? BaseUrl.Result.Valid)?.baseUrl
            }
        }

    val canSave: Boolean get() = !isChecking && !networkAccessBlocked

    val canTest: Boolean get() = !isChecking && !networkAccessBlocked && pendingBaseUrl != null

    /**
     * Whether the note about groups staying with their server is worth showing:
     * only when the address is changing and something saved would look lost.
     */
    val showSwitchNote: Boolean
        get() = savedGroupCount > 0 && pendingBaseUrl != null && pendingBaseUrl != savedBaseUrl

    val showPermissionPrompt: Boolean get() = networkAccessBlocked && !permissionPromptDismissed
}

/**
 * Which instance the app talks to. Not a gate, since Spliit has nothing to
 * sign into. Testing the address and saving it are separate: an instance can be
 * right and merely unreachable right now.
 */
class ServerViewModel(
    private val repository: ServerRepository,
    private val groups: GroupRepository,
    private val networkAccess: NetworkAccess,
) : ViewModel() {
    private val _state = MutableStateFlow(ServerUiState())
    val state: StateFlow<ServerUiState> = _state.asStateFlow()

    init {
        onScreenResumed()
        viewModelScope.launch {
            // Show what is already configured rather than defaulting back.
            val stored = repository.baseUrl.first()
            val saved = groups.groupIdsFor(stored).first()
            _state.update {
                it.copy(
                    savedBaseUrl = stored,
                    savedGroupCount = saved.size,
                    choice = if (BaseUrl.isCloud(stored)) InstanceChoice.Cloud else InstanceChoice.SelfHosted,
                    customUrl = if (BaseUrl.isCloud(stored)) it.customUrl else stored,
                )
            }
        }
    }

    /** Re-checks network access, which is what a trip to Android's settings changes. */
    fun onScreenResumed() {
        viewModelScope.launch {
            // Resolve the slow answer first: passing a suspending call to
            // copy() would read the state, suspend, and write the stale copy.
            val allowed = networkAccess.isAllowed()
            _state.update { it.copy(networkAccessBlocked = !allowed, permissionPromptDismissed = false) }
        }
    }

    fun onPermissionPromptDismissed() {
        _state.update { it.copy(permissionPromptDismissed = true) }
    }

    /** Re-opens the prompt from the inline explanation on the screen. */
    fun onPermissionPromptRequested() {
        _state.update { it.copy(permissionPromptDismissed = false) }
    }

    fun onChoiceChange(choice: InstanceChoice) {
        _state.update { it.copy(choice = choice, urlError = null, checkOutcome = null) }
    }

    fun onUrlChange(url: String) {
        // The verdicts describe text the user has started replacing.
        _state.update { it.copy(customUrl = url, urlError = null, checkOutcome = null) }
    }

    /**
     * Asks the instance whether it is there, with `categories.list`, the one
     * procedure needing no input and no group, so reaching it proves the
     * address is a Spliit server rather than a host that answers.
     */
    fun onTest() {
        val current = _state.value
        if (current.isChecking) return
        if (current.networkAccessBlocked) {
            _state.update { it.copy(permissionPromptDismissed = false) }
            return
        }

        val baseUrl = when (current.choice) {
            InstanceChoice.Cloud -> BaseUrl.CLOUD

            InstanceChoice.SelfHosted -> when (val n = BaseUrl.normalise(current.customUrl)) {
                is BaseUrl.Result.Invalid -> {
                    _state.update { it.copy(urlError = n.reason) }
                    return
                }

                is BaseUrl.Result.Valid -> {
                    n.baseUrl
                }
            }
        }

        _state.update { it.copy(isChecking = true, checkOutcome = null) }
        viewModelScope.launch {
            val outcome = when (val result = repository.check(baseUrl)) {
                is SpliitResult.Success -> CheckOutcome.Reachable
                is SpliitResult.Failure -> CheckOutcome.Failed(result.error)
            }
            // Network access can be revoked while the app runs, and that fails
            // exactly like being offline. Re-read it before blaming the address.
            val blocked = outcome is CheckOutcome.Failed &&
                outcome.error is SpliitError.Network &&
                !networkAccess.isAllowed()
            _state.update {
                it.copy(
                    isChecking = false,
                    checkOutcome = if (blocked) null else outcome,
                    networkAccessBlocked = blocked,
                    permissionPromptDismissed = if (blocked) false else it.permissionPromptDismissed,
                )
            }
        }
    }

    /**
     * Saves the selected address. Deliberately not conditional on a successful
     * test: an address can be right and merely unreachable, a home server the
     * user is away from, and gating here would strand them.
     */
    fun onSave() {
        val current = _state.value
        if (!current.canSave) {
            if (current.networkAccessBlocked) {
                _state.update { it.copy(permissionPromptDismissed = false) }
            }
            return
        }

        val baseUrl = when (current.choice) {
            InstanceChoice.Cloud -> BaseUrl.CLOUD

            InstanceChoice.SelfHosted -> when (val n = BaseUrl.normalise(current.customUrl)) {
                is BaseUrl.Result.Invalid -> {
                    _state.update { it.copy(urlError = n.reason) }
                    return
                }

                is BaseUrl.Result.Valid -> {
                    n.baseUrl
                }
            }
        }

        viewModelScope.launch {
            repository.setBaseUrl(baseUrl)
            _state.update { it.copy(urlError = null, isSaved = true) }
        }
    }

    fun onNavigationHandled() {
        _state.update { it.copy(isSaved = false) }
    }
}
