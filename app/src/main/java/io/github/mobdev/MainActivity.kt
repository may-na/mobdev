package io.github.mobdev

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ContactsApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactsApp() {
    val context = LocalContext.current
    val viewModel: ContactsViewModel = viewModel()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.loadIfNeeded()
    }

    var selectedIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    BackHandler(enabled = selectedIndex != null) {
        selectedIndex = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectedIndex == null) stringResource(R.string.contacts_title)
                        else stringResource(R.string.contact_details_title)
                    )
                },
                navigationIcon = {
                    if (selectedIndex != null) {
                        IconButton(onClick = { selectedIndex = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        ContactsContent(
            innerPadding = innerPadding,
            hasPermission = hasPermission,
            contacts = viewModel.contacts,
            selectedIndex = selectedIndex,
            onRequestPermission = {
                permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            },
            onContactClick = { index -> selectedIndex = index },
            onClearSelection = { selectedIndex = null }
        )
    }
}

@Composable
private fun ContactsContent(
    innerPadding: PaddingValues,
    hasPermission: Boolean,
    contacts: List<Contact>,
    selectedIndex: Int?,
    onRequestPermission: () -> Unit,
    onContactClick: (Int) -> Unit,
    onClearSelection: () -> Unit,
) {
    Box(modifier = Modifier.padding(innerPadding)) {
        when {
            !hasPermission -> NoPermissionScreen(onRequest = onRequestPermission)

            selectedIndex != null -> {
                val contact = contacts.getOrNull(selectedIndex)
                if (contact != null) {
                    ContactDetailsScreen(contact)
                } else {
                    LaunchedEffect(Unit) { onClearSelection() }
                }
            }

            else -> ContactsListScreen(contacts = contacts, onClick = onContactClick)
        }
    }
}

@Composable
private fun NoPermissionScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.permission_required_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.permission_required_message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) {
            Text(stringResource(R.string.permission_request_button))
        }
    }
}

@Composable
private fun ContactsListScreen(
    contacts: List<Contact>,
    onClick: (Int) -> Unit,
) {
    if (contacts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.no_contacts))
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(contacts) { index, contact ->
            ListItem(
                headlineContent = {
                    Text(text = contact.name ?: stringResource(R.string.empty_name))
                },
                modifier = Modifier.clickable { onClick(index) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ContactDetailsScreen(contact: Contact) {
    val emptyValue = stringResource(R.string.empty_value)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DetailRow(
            label = stringResource(R.string.contact_name_label),
            value = contact.name ?: stringResource(R.string.empty_name)
        )
        DetailRow(
            label = stringResource(R.string.contact_phone_label),
            value = contact.phoneNumber ?: emptyValue
        )
        DetailRow(
            label = stringResource(R.string.contact_email_label),
            value = contact.email ?: emptyValue
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}
