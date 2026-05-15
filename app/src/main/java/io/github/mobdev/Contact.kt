package io.github.mobdev

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import androidx.core.database.getStringOrNull

data class Contact(
    val name: String?,
    val phoneNumber: String?,
    val email: String?,
)

@SuppressLint("Range")
fun Context.fetchAllContacts(): List<Contact> {
    return contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        null, null, null, null
    ).use { cursor: Cursor? ->
        if (cursor == null) return emptyList()
        buildList {
            while (cursor.moveToNext()) {
                val name = cursor.stringOrNull(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val phoneNumber = cursor.stringOrNull(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val email = cursor.stringOrNull(ContactsContract.CommonDataKinds.Email.ADDRESS)
                add(Contact(name, phoneNumber, email))
            }
        }
    }
}

private fun Cursor.stringOrNull(columnName: String): String? {
    val index = getColumnIndex(columnName)
    return if (index < 0) null else getStringOrNull(index)
}
