package io.github.mobdev.data

import android.content.Context
import androidx.core.content.edit

class AuthStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveCredentials(name: String, password: String) {
        prefs.edit {
            putString(KEY_NAME, name)
            putString(KEY_PASSWORD, password)
        }
    }

    fun saveToken(token: String) {
        prefs.edit { putString(KEY_TOKEN, token) }
    }

    fun clearToken() {
        prefs.edit { remove(KEY_TOKEN) }
    }

    fun clearAll() {
        prefs.edit {
            remove(KEY_NAME)
            remove(KEY_PASSWORD)
            remove(KEY_TOKEN)
        }
    }

    val name: String? get() = prefs.getString(KEY_NAME, null)
    val password: String? get() = prefs.getString(KEY_PASSWORD, null)
    val token: String? get() = prefs.getString(KEY_TOKEN, null)

    companion object {
        private const val PREFS_NAME = "chat_auth"
        private const val KEY_NAME = "name"
        private const val KEY_PASSWORD = "password"
        private const val KEY_TOKEN = "token"
    }
}
