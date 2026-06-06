package io.github.mobdev

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mobdev.data.AuthStore
import io.github.mobdev.data.ChatItem
import io.github.mobdev.data.ChatRepository
import io.github.mobdev.data.ConnectivityObserver
import io.github.mobdev.data.db.ChatDatabase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val username: String) : AuthState
}

enum class LoginErrorKind { Invalid, Network }

data class UiState(
    val auth: AuthState = AuthState.Loading,
    val pendingLogin: Boolean = false,
    val loginError: LoginErrorKind? = null,
    val isOnline: Boolean = true,
    val channels: List<String> = emptyList(),
    val channelsLoading: Boolean = false,
    val channelsError: Boolean = false,
    val selectedChannel: String? = null,
    val openImage: String? = null,
    val messages: List<ChatItem> = emptyList(),
    val messagesLoading: Boolean = false,
    val messagesLoadingOlder: Boolean = false,
    val messagesHasMore: Boolean = true,
    val messagesError: Boolean = false,
    val sending: Boolean = false,
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db: ChatDatabase = ChatDatabase.get(application)
    private val authStore = AuthStore(application)
    private val repository = ChatRepository(authStore = authStore, db = db)
    private val connectivity = ConnectivityObserver(application)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var channelsJob: Job? = null
    private var messagesJob: Job? = null

    init {
        observeConnectivity()
        bootstrap()
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivity.isOnline.collect { online ->
                val wasOnline = _state.value.isOnline
                _state.update { it.copy(isOnline = online) }
                if (online && !wasOnline && _state.value.auth is AuthState.Authenticated) {
                    syncOnReconnect()
                }
            }
        }
    }

    private fun syncOnReconnect() {
        viewModelScope.launch {
            val drain = repository.drainOutbox()
            if (drain.unauthorized) {
                forceLogout()
                return@launch
            }
            val refresh = repository.refreshChannels()
            if (refresh == ChatRepository.SyncOutcome.Unauthorized) {
                forceLogout()
                return@launch
            }
            _state.update {
                it.copy(channelsError = refresh == ChatRepository.SyncOutcome.Offline && it.channels.isEmpty())
            }
            val channel = _state.value.selectedChannel
            if (channel != null) {
                val res = repository.loadNewer(channel)
                if (res == ChatRepository.SyncOutcome.Unauthorized) forceLogout()
            }
        }
    }

    private fun bootstrap() {
        if (!repository.hasStoredCredentials) {
            _state.update { it.copy(auth = AuthState.Unauthenticated) }
            return
        }
        viewModelScope.launch {
            when (repository.loginWithStoredCredentials()) {
                ChatRepository.LoginResult.Success -> onAuthenticated()
                ChatRepository.LoginResult.InvalidCredentials -> _state.update {
                    it.copy(auth = AuthState.Unauthenticated)
                }
                ChatRepository.LoginResult.NetworkError -> {
                    if (repository.hasChannelsCached()) {
                        onAuthenticated()
                    } else {
                        _state.update { it.copy(auth = AuthState.Unauthenticated) }
                    }
                }
            }
        }
    }

    fun onLogin(name: String, password: String) {
        if (name.isBlank() || password.isBlank()) return
        _state.update { it.copy(pendingLogin = true, loginError = null) }
        viewModelScope.launch {
            when (repository.login(name.trim(), password)) {
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
            channelsJob?.cancel(); channelsJob = null
            messagesJob?.cancel(); messagesJob = null
            repository.logout()
            _state.value = UiState(
                auth = AuthState.Unauthenticated,
                isOnline = _state.value.isOnline,
            )
        }
    }

    fun onSelectChannel(channel: String) {
        if (_state.value.selectedChannel == channel) return
        _state.update {
            it.copy(
                selectedChannel = channel,
                openImage = null,
                messages = emptyList(),
                messagesLoading = true,
                messagesLoadingOlder = false,
                messagesHasMore = true,
                messagesError = false,
            )
        }
        startObservingMessages(channel)
        viewModelScope.launch {
            val result = repository.loadInitialMessages(channel)
            _state.update {
                it.copy(
                    messagesLoading = false,
                    messagesError = result == ChatRepository.SyncOutcome.Offline && it.messages.isEmpty(),
                )
            }
            if (result == ChatRepository.SyncOutcome.Unauthorized) forceLogout()
        }
    }

    fun onCloseChannel() {
        _state.update {
            it.copy(
                selectedChannel = null,
                openImage = null,
                messages = emptyList(),
                messagesLoading = false,
                messagesLoadingOlder = false,
                messagesError = false,
                messagesHasMore = true,
            )
        }
        messagesJob?.cancel()
        messagesJob = null
    }

    fun onOpenImage(path: String) {
        _state.update { it.copy(openImage = path) }
    }

    fun onCloseImage() {
        _state.update { it.copy(openImage = null) }
    }

    fun onRetryChats() {
        viewModelScope.launch {
            _state.update { it.copy(channelsLoading = it.channels.isEmpty(), channelsError = false) }
            val r = repository.refreshChannels()
            _state.update {
                it.copy(
                    channelsLoading = false,
                    channelsError = r == ChatRepository.SyncOutcome.Offline && it.channels.isEmpty(),
                )
            }
            if (r == ChatRepository.SyncOutcome.Unauthorized) forceLogout()
        }
    }

    fun onLoadOlder() {
        val channel = _state.value.selectedChannel ?: return
        val current = _state.value
        if (current.messagesLoadingOlder || !current.messagesHasMore) return
        _state.update { it.copy(messagesLoadingOlder = true) }
        viewModelScope.launch {
            val res = repository.loadOlder(channel)
            _state.update {
                it.copy(
                    messagesLoadingOlder = false,
                    messagesHasMore = res.moreAvailable,
                )
            }
            if (res.outcome == ChatRepository.SyncOutcome.Unauthorized) forceLogout()
        }
    }

    fun onSend(text: String) {
        val channel = _state.value.selectedChannel ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            val result = repository.sendOrQueue(channel, trimmed)
            _state.update { it.copy(sending = false) }
            if (result is ChatRepository.SendOutcome.Unauthorized) forceLogout()
        }
    }

    private fun onAuthenticated() {
        val username = repository.currentUser ?: return
        _state.update { it.copy(auth = AuthState.Authenticated(username)) }
        startObservingChannels()
        viewModelScope.launch {
            val hasCache = repository.hasChannelsCached()
            _state.update { it.copy(channelsLoading = !hasCache, channelsError = false) }
            val r = repository.refreshChannels()
            _state.update {
                it.copy(
                    channelsLoading = false,
                    channelsError = r == ChatRepository.SyncOutcome.Offline && it.channels.isEmpty(),
                )
            }
            if (r == ChatRepository.SyncOutcome.Unauthorized) forceLogout()
        }
    }

    private fun startObservingChannels() {
        channelsJob?.cancel()
        channelsJob = viewModelScope.launch {
            repository.observeChannels().collect { names ->
                _state.update { it.copy(channels = names) }
            }
        }
    }

    private fun startObservingMessages(channel: String) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            repository.observeMessages(channel).collect { items ->
                _state.update { state ->
                    if (state.selectedChannel != channel) state
                    else state.copy(messages = items)
                }
            }
        }
    }

    private fun forceLogout() {
        viewModelScope.launch {
            channelsJob?.cancel(); channelsJob = null
            messagesJob?.cancel(); messagesJob = null
            repository.logout()
            _state.value = UiState(
                auth = AuthState.Unauthenticated,
                isOnline = _state.value.isOnline,
            )
        }
    }
}
