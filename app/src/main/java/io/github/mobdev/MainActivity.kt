package io.github.mobdev

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import io.github.mobdev.data.ChatItem
import io.github.mobdev.data.MessageContent
import io.github.mobdev.data.Network

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ChatApp()
                }
            }
        }
    }
}

@Composable
private fun ChatApp() {
    val vm: ChatViewModel = viewModel()
    val state by vm.state.collectAsState()

    when (state.auth) {
        AuthState.Loading -> SplashScreen()
        AuthState.Unauthenticated -> LoginScreen(
            pending = state.pendingLogin,
            error = state.loginError,
            onSubmit = vm::onLogin,
            onDismissError = vm::dismissLoginError,
        )
        is AuthState.Authenticated -> AuthenticatedRoot(state = state, vm = vm)
    }
}

@Composable
private fun SplashScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginScreen(
    pending: Boolean,
    error: LoginErrorKind?,
    onSubmit: (String, String) -> Unit,
    onDismissError: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val canSubmit = !pending && name.isNotBlank() && password.isNotBlank()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.login_title)) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.login_name_label)) },
                singleLine = true,
                enabled = !pending,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.login_password_label)) },
                singleLine = true,
                enabled = !pending,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onSubmit(name, password) },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (pending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.login_button))
                }
            }
        }
    }

    if (error != null) {
        val message = when (error) {
            LoginErrorKind.Invalid -> stringResource(R.string.login_error_invalid)
            LoginErrorKind.Network -> stringResource(R.string.login_error_network)
        }
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text(stringResource(R.string.login_error_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = onDismissError) {
                    Text(stringResource(R.string.login_error_dismiss))
                }
            },
        )
    }
}

@Composable
private fun AuthenticatedRoot(state: UiState, vm: ChatViewModel) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    BackHandler(enabled = state.openImage != null) { vm.onCloseImage() }
    BackHandler(enabled = state.openImage == null && state.selectedChannel != null) {
        vm.onCloseChannel()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!state.isOnline) OfflineBanner()
        Box(modifier = Modifier.weight(1f)) {
            if (isLandscape) {
                LandscapeLayout(state = state, vm = vm)
            } else {
                PortraitLayout(state = state, vm = vm)
            }
        }
    }
}

@Composable
private fun OfflineBanner() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.offline_banner),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PortraitLayout(state: UiState, vm: ChatViewModel) {
    val selected = state.selectedChannel
    val image = state.openImage
    when {
        image != null && selected != null -> ImageScreen(
            imagePath = image,
            onClose = vm::onCloseImage,
        )
        selected != null -> MessagesScreen(
            channel = selected,
            state = state,
            onBack = vm::onCloseChannel,
            onLoadOlder = vm::onLoadOlder,
            onSend = vm::onSend,
            onOpenImage = vm::onOpenImage,
        )
        else -> ChatsListScreen(
            state = state,
            selectedChannel = null,
            onClick = vm::onSelectChannel,
            onLogout = vm::onLogout,
            onRetry = vm::onRetryChats,
        )
    }
}

@Composable
private fun LandscapeLayout(state: UiState, vm: ChatViewModel) {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            ChatsListScreen(
                state = state,
                selectedChannel = state.selectedChannel,
                onClick = vm::onSelectChannel,
                onLogout = vm::onLogout,
                onRetry = vm::onRetryChats,
            )
        }
        Box(
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            val selected = state.selectedChannel
            if (selected == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.chats_select_prompt),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                MessagesScreen(
                    channel = selected,
                    state = state,
                    onBack = vm::onCloseChannel,
                    onLoadOlder = vm::onLoadOlder,
                    onSend = vm::onSend,
                    onOpenImage = vm::onOpenImage,
                    showBackButton = false,
                )
            }
            val image = state.openImage
            if (image != null) {
                ImageScreen(
                    imagePath = image,
                    onClose = vm::onCloseImage,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatsListScreen(
    state: UiState,
    selectedChannel: String?,
    onClick: (String) -> Unit,
    onLogout: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chats_title)) },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = stringResource(R.string.chats_logout),
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                state.channelsLoading && state.channels.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                state.channelsError && state.channels.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.chats_load_error),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text(stringResource(R.string.chats_retry)) }
                }
                state.channels.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.chats_empty))
                }
                else -> ChannelsList(
                    channels = state.channels,
                    selectedChannel = selectedChannel,
                    onClick = onClick,
                )
            }
        }
    }
}

@Composable
private fun ChannelsList(
    channels: List<String>,
    selectedChannel: String?,
    onClick: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(channels, key = { it }) { name ->
            val isSelected = name == selectedChannel
            val background = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                Color.Transparent
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(background)
                    .clickable { onClick(name) }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessagesScreen(
    channel: String,
    state: UiState,
    onBack: () -> Unit,
    onLoadOlder: () -> Unit,
    onSend: (String) -> Unit,
    onOpenImage: (String) -> Unit,
    showBackButton: Boolean = true,
) {
    var draft by rememberSaveable(channel) { mutableStateOf("") }
    val listState = rememberLazyListState()
    var lastSortKey by rememberSaveable(channel) { mutableLongStateOf(Long.MIN_VALUE) }

    LaunchedEffect(channel, state.messages.lastOrNull()?.sortKey) {
        val newest = state.messages.lastOrNull()?.sortKey ?: return@LaunchedEffect
        if (newest > lastSortKey) {
            lastSortKey = newest
            listState.scrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.messages_title, channel)) },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            MessageInputBar(
                value = draft,
                onValueChange = { draft = it },
                sending = state.sending,
                onSend = {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        onSend(text)
                        draft = ""
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                state.messagesLoading && state.messages.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                state.messagesError && state.messages.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(
                            if (state.isOnline) R.string.messages_load_error
                            else R.string.messages_load_error_offline
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
                state.messages.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.messages_empty))
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.messagesHasMore && state.isOnline) {
                        item("load-older") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (state.messagesLoadingOlder) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    TextButton(onClick = onLoadOlder) {
                                        Text(stringResource(R.string.messages_load_more))
                                    }
                                }
                            }
                        }
                    }
                    items(state.messages, key = { it.itemKey() }) { item ->
                        MessageRow(item = item, onOpenImage = onOpenImage)
                    }
                }
            }
        }
    }
}

private fun ChatItem.itemKey(): String = when (this) {
    is ChatItem.Server -> "s-${message.id}"
    is ChatItem.Pending -> "p-$localId"
}

@Composable
private fun MessageRow(
    item: ChatItem,
    onOpenImage: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        when (item) {
            is ChatItem.Server -> {
                val message = item.message
                Text(
                    text = message.from,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(2.dp))
                when (val content = message.content) {
                    is MessageContent.Text -> Text(
                        text = content.text,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    is MessageContent.Image -> AsyncImage(
                        model = Network.thumbUrl(content.link),
                        contentDescription = stringResource(R.string.image_content_description),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenImage(content.link) },
                    )
                    MessageContent.Unknown -> Text(
                        text = "—",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            is ChatItem.Pending -> {
                Text(
                    text = item.from,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                )
                Text(
                    text = stringResource(R.string.messages_pending),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                )
            }
        }
    }
}

@Composable
private fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    sending: Boolean,
    onSend: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(stringResource(R.string.messages_send_hint)) },
                modifier = Modifier.weight(1f),
                enabled = !sending,
                maxLines = 4,
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = !sending && value.trim().isNotEmpty(),
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.messages_send),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageScreen(
    imagePath: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.image_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = Network.imgUrl(imagePath),
                contentDescription = stringResource(R.string.image_content_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
