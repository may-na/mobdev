package io.github.mobdev

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

class ContactsViewModel(application: Application) : AndroidViewModel(application) {

    var contacts by mutableStateOf<List<Contact>>(emptyList())
        private set

    private var loaded = false

    fun loadIfNeeded() {
        if (loaded) return
        contacts = getApplication<Application>().fetchAllContacts()
        loaded = true
    }
}
