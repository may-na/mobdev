package io.github.mobdev

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import androidx.core.database.getStringOrNull

data class Contact(
    val name: String?,
    val phoneNumber: String?,
    val email: String?,
)

fun Context.fetchAllContacts(): List<Contact> {
    val emailsByContactId = readEmailsByContactId()

    return contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        ),
        null, null, null
    ).use { cursor: Cursor? ->
        if (cursor == null) return emptyList()
        buildList {
            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(0)
                val name = cursor.getStringOrNull(1)
                val phoneNumber = cursor.getStringOrNull(2)
                add(Contact(name, phoneNumber, emailsByContactId[contactId]))
            }
        }
    }
}

private fun Context.readEmailsByContactId(): Map<Long, String> {
    val result = mutableMapOf<Long, String>()
    contentResolver.query(
        ContactsContract.CommonDataKinds.Email.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Email.CONTACT_ID,
            ContactsContract.CommonDataKinds.Email.ADDRESS,
        ),
        null, null, null
    ).use { cursor: Cursor? ->
        if (cursor == null) return result
        while (cursor.moveToNext()) {
            val contactId = cursor.getLong(0)
            val email = cursor.getStringOrNull(1)
            if (email != null) result.putIfAbsent(contactId, email)
        }
    }
    return result
}
