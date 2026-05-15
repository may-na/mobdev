package io.github.mobdev

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mobdev.data.AuthStore
import io.github.mobdev.data.ChatRepository
import io.github.mobdev.data.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20

sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val username: String) : AuthState
}

enum class LoginErrorKind { Invalid, Network }

sealed interface ChatsState {
    data object Idle : ChatsState
    data object Loading : ChatsState
    data class Loaded(val channels: List<String>) : ChatsState
    data object Error : ChatsState
}

data class MessagesState(
    val messages: List<Message> = emptyList(),
    val loading: Boolean = false,
    val loadingOlder: Boolean = false,
    val hasMore: Boolean = true,
    val error: Boolean = false,
)

data class UiState(
    val auth: AuthState = AuthState.Loading,
    val pendingLogin: Boolean = false,
    val loginError: LoginErrorKind? = null,
    val chats: ChatsState = ChatsState.Idle,
    val selectedChannel: String? = null,
    val openImage: String? = null,
    val messagesByChannel: Map<String, MessagesState> = emptyMap(),
    val sending: Boolean = false,
    val sendError: Boolean = false,
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ChatRepository = ChatRepository(authStore = AuthStore(application))

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        bootstrap()
    }

    private fun bootstrap() {
        if (!repository.hasStoredCredentials) {
            _state.update { it.copy(auth = AuthState.Unauthenticated) }
            return
        }
        viewModelScope.launch {
            when (repository.loginWithStoredCredentials()) {
                ChatRepository.LoginResult.Success -> onAuthenticated()
                ChatRepository.LoginResult.InvalidCredentials,
                ChatRepository.LoginResult.NetworkError -> {
                    _state.update { it.copy(auth = AuthState.Unauthenticated) }
                }
            }
        }
    }

    fun onLogin(name: String, password: String) {
        if (name.isBlank() || password.isBlank()) return
        _state.update { it.copy(pendingLogin = true, loginError = null) }
        viewModelScope.launch {
            val result = repository.login(name.trim(), password)
            when (result) {
                ChatRepository.LoginResult.Success -> {
                    _state.update { it.copy(pendingLogin = false, loginError = null) }
                    onAuthenticated()
                }
                ChatRepository.LoginResult.InvalidCredentials -> _state.update {
                    it.copy(pendingLogin = false, loginError = LoginErrorKind.Invalid)
                }
                ChatRepository.LoginResult.NetworkError -> _state.update {
                    it.copy(pendingLogin = false, loginError = LoginErrorKind.Network)
                }
            }
        }
    }

    fun dismissLoginError() {
        _state.update { it.copy(loginError = null) }
    }

    fun onLogout() {
        viewModelScope.launch {
            repository.logout()
            _state.value = UiState(auth = AuthState.Unauthenticated)
        }
    }

    fun onSelectChannel(channel: String) {
        _state.update { it.copy(selectedChannel = channel, openImage = null) }
        val existing = _state.value.messagesByChannel[channel]
        if (existing == null || (existing.messages.isEmpty() && !existing.loading)) {
            loadInitialMessages(channel)
        }
    }

    fun onCloseChannel() {
        _state.update { it.copy(selectedChannel = null, openImage = null) }
    }

    fun onOpenImage(path: String) {
        _state.update { it.copy(openImage = path) }
    }

    fun onCloseImage() {
        _state.update { it.copy(openImage = null) }
    }

    fun onRetryChats() {
        loadChats()
    }

    fun onLoadOlder() {
        val channel = _state.value.selectedChannel ?: return
        val st = _state.value.messagesByChannel[channel] ?: return
        if (st.loadingOlder || !st.hasMore) return
        val oldestId = st.messages.firstOrNull()?.id ?: return
        updateChannel(channel) { it.copy(loadingOlder = true) }
        viewModelScope.launch {
            val result = repository.messages(
                channel = channel,
                limit = PAGE_SIZE,
                lastKnownId = oldestId,
                reverse = true,
            )
            handleAuthFromResult(result)
            when (result) {
                is ChatRepository.CallResult.Ok -> {
                    val newer = result.value.sortedBy { it.id }
                    updateChannel(channel) {
                        it.copy(
                            messages = newer + it.messages,
                            loadingOlder = false,
                            hasMore = result.value.size >= PAGE_SIZE,
                        )
                    }
                }
                else -> updateChannel(channel) { it.copy(loadingOlder = false) }
            }
        }
    }

    fun onSend(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val channel = _state.value.selectedChannel ?: return
        _state.update { it.copy(sending = true, sendError = false) }
        viewModelScope.launch {
            val result = repository.sendText(channel, trimmed)
            handleAuthFromResult(result)
            when (result) {
                is ChatRepository.CallResult.Ok -> {
                    _state.update { it.copy(sending = false) }
                    refreshLatest(channel)
                }
                ChatRepository.CallResult.Unauthorized -> _state.update {
                    it.copy(sending = false)
                }
                ChatRepository.CallResult.Error -> _state.update {
                    it.copy(sending = false, sendError = true)
                }
            }
        }
    }

    fun dismissSendError() {
        _state.update { it.copy(sendError = false) }
    }

    private fun onAuthenticated() {
        val username = repository.currentUser ?: return
        _state.update { it.copy(auth = AuthState.Authenticated(username)) }
        if (_state.value.chats !is ChatsState.Loaded) {
            loadChats()
        }
    }

    private fun loadChats() {
        _state.update { it.copy(chats = ChatsState.Loading) }
        viewModelScope.launch {
            val result = repository.channels()
            handleAuthFromResult(result)
            when (result) {
                is ChatRepository.CallResult.Ok -> _state.update {
                    it.copy(chats = ChatsState.Loaded(result.value))
                }
                ChatRepository.CallResult.Unauthorized -> _state.update {
                    it.copy(chats = ChatsState.Idle)
                }
                ChatRepository.CallResult.Error -> _state.update {
                    it.copy(chats = ChatsState.Error)
                }
            }
        }
    }

    private fun loadInitialMessages(channel: String) {
        updateChannel(channel) { it.copy(loading = true, error = false) }
        viewModelScope.launch {
            val result = repository.messages(
                channel = channel,
                limit = PAGE_SIZE,
                lastKnownId = Long.MAX_VALUE,
                reverse = true,
            )
            handleAuthFromResult(result)
            when (result) {
                is ChatRepository.CallResult.Ok -> {
                    val sorted = result.value.sortedBy { it.id }
                    updateChannel(channel) {
                        it.copy(
                            messages = sorted,
                            loading = false,
                            hasMore = result.value.size >= PAGE_SIZE,
                            error = false,
                        )
                    }
                }
                ChatRepository.CallResult.Unauthorized -> updateChannel(channel) {
                    it.copy(loading = false)
                }
                ChatRepository.CallResult.Error -> updateChannel(channel) {
                    it.copy(loading = false, error = true)
                }
            }
        }
    }

    private suspend fun refreshLatest(channel: String) {
        val current = _state.value.messagesByChannel[channel]
        val knownMax = current?.messages?.maxOfOrNull { it.id } ?: 0L
        val result = repository.messages(
            channel = channel,
            limit = PAGE_SIZE,
            lastKnownId = knownMax,
            reverse = false,
        )
        handleAuthFromResult(result)
        if (result is ChatRepository.CallResult.Ok) {
            val newOnes = result.value.sortedBy { it.id }
            if (newOnes.isNotEmpty()) {
                updateChannel(channel) { it.copy(messages = it.messages + newOnes) }
            }
        }
    }

    private fun handleAuthFromResult(result: ChatRepository.CallResult<*>) {
        if (result is ChatRepository.CallResult.Unauthorized) {
            _state.value = UiState(auth = AuthState.Unauthenticated)
        }
    }

    private fun updateChannel(channel: String, transform: (MessagesState) -> MessagesState) {
        _state.update { state ->
            val current = state.messagesByChannel[channel] ?: MessagesState()
            state.copy(messagesByChannel = state.messagesByChannel + (channel to transform(current)))
        }
    }
}
