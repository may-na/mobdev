package io.github.mobdev.data

import android.util.Log
import io.github.mobdev.data.db.ChatDatabase
import io.github.mobdev.data.db.OutboxEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

private const val TAG = "ChatRepository"
private const val PAGE_SIZE = 20

class ChatRepository(
    private val api: ChatApi = Network.api,
    private val authStore: AuthStore,
    db: ChatDatabase,
) {
    private val messageDao = db.messageDao()
    private val channelDao = db.channelDao()
    private val outboxDao = db.outboxDao()

    sealed interface LoginResult {
        data object Success : LoginResult
        data object InvalidCredentials : LoginResult
        data object NetworkError : LoginResult
    }

    enum class SyncOutcome { Ok, Offline, Unauthorized }

    data class LoadOlderOutcome(val outcome: SyncOutcome, val moreAvailable: Boolean)

    sealed interface SendOutcome {
        data object Sent : SendOutcome
        data class Queued(val localId: Long) : SendOutcome
        data object Unauthorized : SendOutcome
    }

    data class DrainOutcome(val sent: Int, val failed: Int, val unauthorized: Boolean)

    val currentUser: String? get() = authStore.name
    val hasStoredCredentials: Boolean
        get() = authStore.name != null && authStore.password != null

    fun observeChannels(): Flow<List<String>> = channelDao.observeNames()

    fun observeMessages(channel: String): Flow<List<ChatItem>> =
        combine(
            messageDao.observeByChannel(channel),
            outboxDao.observeByChannel(channel),
        ) { server, pending ->
            val me = authStore.name.orEmpty()
            val serverItems = server.map { ChatItem.Server(it.toDomain()) }
            val pendingItems = pending.map {
                ChatItem.Pending(
                    localId = it.localId,
                    from = me,
                    text = it.text,
                    createdAt = it.createdAt,
                )
            }
            (serverItems + pendingItems).sortedBy { it.sortKey }
        }

    suspend fun hasChannelsCached(): Boolean = channelDao.count() > 0

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
        clearLocalCache()
    }

    private suspend fun clearLocalCache() {
        messageDao.clear()
        channelDao.clear()
        outboxDao.clear()
    }

    suspend fun refreshChannels(): SyncOutcome {
        val token = authStore.token ?: return SyncOutcome.Unauthorized
        return try {
            val response = api.channels(token)
            when {
                response.isSuccessful -> {
                    channelDao.replaceAll(response.body().orEmpty())
                    SyncOutcome.Ok
                }
                response.code() == 401 -> {
                    authStore.clearToken()
                    SyncOutcome.Unauthorized
                }
                else -> {
                    Log.w(TAG, "refreshChannels: HTTP ${response.code()}")
                    SyncOutcome.Offline
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "refreshChannels: network exception", e)
            SyncOutcome.Offline
        }
    }

    suspend fun loadInitialMessages(channel: String): SyncOutcome {
        val hasCached = messageDao.countByChannel(channel) > 0
        return if (hasCached) {
            loadNewer(channel)
        } else {
            fetchAndStore(channel, lastKnownId = Long.MAX_VALUE, reverse = true)
        }
    }

    suspend fun loadOlder(channel: String): LoadOlderOutcome {
        val oldestId = messageDao.minId(channel)
            ?: return LoadOlderOutcome(SyncOutcome.Ok, moreAvailable = false)
        val token = authStore.token
            ?: return LoadOlderOutcome(SyncOutcome.Unauthorized, moreAvailable = true)
        return try {
            val response = api.messages(
                token = token,
                name = channel,
                limit = PAGE_SIZE,
                lastKnownId = oldestId,
                reverse = true,
            )
            when {
                response.isSuccessful -> {
                    val list = response.body().orEmpty()
                    messageDao.upsertAll(list.map { it.toEntity(channel) })
                    LoadOlderOutcome(
                        outcome = SyncOutcome.Ok,
                        moreAvailable = list.size >= PAGE_SIZE,
                    )
                }
                response.code() == 401 -> {
                    authStore.clearToken()
                    LoadOlderOutcome(SyncOutcome.Unauthorized, moreAvailable = true)
                }
                else -> LoadOlderOutcome(SyncOutcome.Offline, moreAvailable = true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "loadOlder: network exception", e)
            LoadOlderOutcome(SyncOutcome.Offline, moreAvailable = true)
        }
    }

    suspend fun loadNewer(channel: String): SyncOutcome {
        val knownMax = messageDao.maxId(channel) ?: 0L
        return fetchAndStore(channel, lastKnownId = knownMax, reverse = false)
    }

    private suspend fun fetchAndStore(
        channel: String,
        lastKnownId: Long,
        reverse: Boolean,
    ): SyncOutcome {
        val token = authStore.token ?: return SyncOutcome.Unauthorized
        return try {
            val response = api.messages(
                token = token,
                name = channel,
                limit = PAGE_SIZE,
                lastKnownId = lastKnownId,
                reverse = reverse,
            )
            when {
                response.isSuccessful -> {
                    messageDao.upsertAll(response.body().orEmpty().map { it.toEntity(channel) })
                    SyncOutcome.Ok
                }
                response.code() == 401 -> {
                    authStore.clearToken()
                    SyncOutcome.Unauthorized
                }
                else -> {
                    Log.w(TAG, "fetchAndStore($channel): HTTP ${response.code()}")
                    SyncOutcome.Offline
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchAndStore($channel): network exception", e)
            SyncOutcome.Offline
        }
    }

    suspend fun sendOrQueue(channel: String, text: String): SendOutcome {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return SendOutcome.Sent
        val name = authStore.name ?: return SendOutcome.Unauthorized
        val token = authStore.token
        if (token != null) {
            when (val direct = trySend(token, name, channel, trimmed)) {
                SendOutcome.Sent -> {
                    loadNewer(channel)
                    return SendOutcome.Sent
                }
                SendOutcome.Unauthorized -> return SendOutcome.Unauthorized
                is SendOutcome.Queued -> Unit
            }
        }
        val localId = outboxDao.insert(
            OutboxEntity(
                channel = channel,
                text = trimmed,
                createdAt = System.currentTimeMillis(),
            )
        )
        return SendOutcome.Queued(localId)
    }

    suspend fun drainOutbox(): DrainOutcome {
        val token = authStore.token ?: return DrainOutcome(0, 0, unauthorized = true)
        val name = authStore.name ?: return DrainOutcome(0, 0, unauthorized = true)
        var sent = 0
        var failed = 0
        var unauthorized = false
        val touchedChannels = mutableSetOf<String>()
        for (entry in outboxDao.pending()) {
            when (trySend(token, name, entry.channel, entry.text)) {
                SendOutcome.Sent -> {
                    outboxDao.deleteById(entry.localId)
                    touchedChannels += entry.channel
                    sent++
                }
                SendOutcome.Unauthorized -> {
                    unauthorized = true
                    break
                }
                is SendOutcome.Queued -> {
                    outboxDao.incrementAttempts(entry.localId)
                    failed++
                    break
                }
            }
        }
        for (channel in touchedChannels) loadNewer(channel)
        return DrainOutcome(sent = sent, failed = failed, unauthorized = unauthorized)
    }

    private suspend fun trySend(
        token: String,
        from: String,
        channel: String,
        text: String,
    ): SendOutcome = try {
        val response = api.send(
            token,
            SendMessageDto(
                from = from,
                to = channel,
                data = MessageDataDto(text = TextDataDto(text)),
            )
        )
        when {
            response.isSuccessful -> SendOutcome.Sent
            response.code() == 401 -> {
                authStore.clearToken()
                SendOutcome.Unauthorized
            }
            else -> {
                Log.w(TAG, "send: HTTP ${response.code()}")
                SendOutcome.Queued(-1L)
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "send: network exception", e)
        SendOutcome.Queued(-1L)
    }
}
