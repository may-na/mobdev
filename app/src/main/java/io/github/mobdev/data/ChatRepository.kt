package io.github.mobdev.data

import android.util.Log
import retrofit2.Response

private const val TAG = "ChatRepository"

class ChatRepository(
    private val api: ChatApi = Network.api,
    private val authStore: AuthStore,
) {
    sealed interface LoginResult {
        data object Success : LoginResult
        data object InvalidCredentials : LoginResult
        data object NetworkError : LoginResult
    }

    sealed interface CallResult<out T> {
        data class Ok<T>(val value: T) : CallResult<T>
        data object Unauthorized : CallResult<Nothing>
        data object Error : CallResult<Nothing>
    }

    suspend fun login(name: String, password: String): LoginResult = try {
        val response = api.login(LoginRequestDto(name = name, pwd = password))
        when {
            response.isSuccessful -> {
                val token = response.body()?.trim().orEmpty()
                if (token.isEmpty()) {
                    Log.w(TAG, "login: empty token in successful response")
                    LoginResult.NetworkError
                } else {
                    authStore.saveCredentials(name, password)
                    authStore.saveToken(token)
                    LoginResult.Success
                }
            }
            response.code() == 401 || response.code() == 403 -> LoginResult.InvalidCredentials
            else -> {
                Log.w(TAG, "login: unexpected HTTP ${response.code()}")
                LoginResult.NetworkError
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "login: network exception", e)
        LoginResult.NetworkError
    }

    suspend fun loginWithStoredCredentials(): LoginResult {
        val name = authStore.name ?: return LoginResult.InvalidCredentials
        val password = authStore.password ?: return LoginResult.InvalidCredentials
        return login(name, password)
    }

    suspend fun logout() {
        val token = authStore.token
        if (token != null) {
            runCatching { api.logout(token) }
        }
        authStore.clearAll()
    }

    suspend fun channels(): CallResult<List<String>> = authedCall { token ->
        api.channels(token)
    }

    suspend fun messages(
        channel: String,
        limit: Int = 20,
        lastKnownId: Long = Long.MAX_VALUE,
        reverse: Boolean = true,
    ): CallResult<List<Message>> {
        val raw = authedCall { token ->
            api.messages(token, channel, limit, lastKnownId, reverse)
        }
        return when (raw) {
            is CallResult.Ok -> CallResult.Ok(raw.value.map { it.toDomain() })
            CallResult.Unauthorized -> CallResult.Unauthorized
            CallResult.Error -> CallResult.Error
        }
    }

    suspend fun sendText(channel: String, text: String): CallResult<Unit> {
        val name = authStore.name ?: return CallResult.Unauthorized
        val raw = authedCall { token ->
            api.send(
                token,
                SendMessageDto(
                    from = name,
                    to = channel,
                    data = MessageDataDto(text = TextDataDto(text)),
                )
            )
        }
        return when (raw) {
            is CallResult.Ok -> CallResult.Ok(Unit)
            CallResult.Unauthorized -> CallResult.Unauthorized
            CallResult.Error -> CallResult.Error
        }
    }

    val currentUser: String? get() = authStore.name
    val hasStoredCredentials: Boolean
        get() = authStore.name != null && authStore.password != null

    private suspend fun <T> authedCall(block: suspend (String) -> Response<T>): CallResult<T> {
        val token = authStore.token ?: return CallResult.Unauthorized
        return try {
            val response = block(token)
            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body != null) CallResult.Ok(body) else CallResult.Error
                }
                response.code() == 401 -> {
                    authStore.clearToken()
                    CallResult.Unauthorized
                }
                else -> CallResult.Error
            }
        } catch (_: Exception) {
            CallResult.Error
        }
    }
}
