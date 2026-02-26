package slowscript.warpinator.feature.home.panes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import slowscript.warpinator.core.data.WarpinatorViewModel
import slowscript.warpinator.core.design.components.DynamicAvatarCircle
import slowscript.warpinator.core.design.theme.WarpinatorTheme
import slowscript.warpinator.core.model.Message
import slowscript.warpinator.core.model.Remote
import slowscript.warpinator.core.model.Transfer
import slowscript.warpinator.core.utils.RemoteDisplayInfo
import slowscript.warpinator.feature.home.components.MessageBubble

@Composable
fun MessagesPane(
    remote: Remote,
    paneMode: Boolean,
    onBack: () -> Unit,
    viewModel: WarpinatorViewModel = hiltViewModel(),
) {
    MessagesPaneContent(
        remote = remote,
        paneMode = paneMode,
        onBack = onBack,
        onSendMessage = { message -> viewModel.sendTextMessage(remote, message) },
        onMarkAsRead = { viewModel.markTextMessagesAsRead(remote) },
    )
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessagesPaneContent(
    remote: Remote,
    paneMode: Boolean,
    onBack: () -> Unit = {},
    onSendMessage: (String) -> Unit = {},
    onMarkAsRead: () -> Unit = {},
) {
    val titleFormat = RemoteDisplayInfo.fromRemote(remote)
    var messageText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(remote.messages.size) {
        onMarkAsRead()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = if (paneMode) WindowInsets() else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            TopAppBar(
                title = {
                    if (paneMode) Text(titleFormat.title) else Row(verticalAlignment = Alignment.CenterVertically) {
                        DynamicAvatarCircle(bitmap = remote.picture, isFavorite = remote.isFavorite)
                        Text(titleFormat.title, modifier = Modifier.padding(8.dp, 0.dp))
                    }
                },
                navigationIcon = {
                    if (!paneMode) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            )
        },

        ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding + PaddingValues(
                    vertical = 8.dp,
                ) + PaddingValues(bottom = 100.dp),
                reverseLayout = true,
            ) {
                items(
                    items = remote.messages,
                    key = { message -> message.timestamp },
                ) { message ->
                    MessageBubble(message)
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(innerPadding)
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Message...") },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        maxLines = 3,
                    )

                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                onSendMessage(messageText)
                                messageText = ""
                            }
                        },
                        enabled = messageText.isNotBlank(),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send",
                            tint = if (messageText.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun MessagesPanePreview() {
    // An AI generated conversation for testing purposes
    val messages = listOf(
        Message(
            remoteUuid = "remote",
            direction = Transfer.Direction.Send,
            timestamp = 1,
            text = "Hey! Are you around?",
        ),
        Message(
            remoteUuid = "remote",
            direction = Transfer.Direction.Receive,
            timestamp = 2,
            text = "Hey there! Yeah, just finished some work. What's up?",
        ),
        Message(
            remoteUuid = "remote",
            direction = Transfer.Direction.Send,
            timestamp = 3,
            text = "I was thinking about that new cafe downtown. Want to check it out?",
        ),
        Message(
            remoteUuid = "remote",
            direction = Transfer.Direction.Receive,
            timestamp = 4,
            text = "Oh, the one with the blue storefront? I've heard the espresso is incredible.",
        ),
        Message(
            remoteUuid = "remote",
            direction = Transfer.Direction.Send,
            timestamp = 5,
            text = "Exactly that one! They also have those huge croissants everyone is posting about.",
        ),
        Message(
            remoteUuid = "remote",
            direction = Transfer.Direction.Receive,
            timestamp = 6,
            text = "Haha, count me in. I'm a sucker for a good pastry.",
        ),
        Message(
            remoteUuid = "remote",
            direction = Transfer.Direction.Send,
            timestamp = 7,
            text = "Great! Does 4:00 PM work for you?",
        ),
        Message(
            remoteUuid = "remote",
            direction = Transfer.Direction.Receive,
            timestamp = 8,
            text = "Make it 4:15? I need to walk the dog first.",
        ),
        Message(
            remoteUuid = "remote",
            direction = Transfer.Direction.Send,
            timestamp = 9,
            text = "No problem at all. 4:15 it is.",
        ),
        Message(
            remoteUuid = "remote",
            direction = Transfer.Direction.Receive,
            timestamp = 10,
            text = "Perfect. See you there!",
        ),
        Message(
            remoteUuid = "remote",
            direction = Transfer.Direction.Send,
            timestamp = 11,
            text = "See ya!",
        ),
        Message(
            remoteUuid = "remote",
            direction = Transfer.Direction.Receive,
            timestamp = 12,
            text = "Wait, should I bring that book I borrowed from you?",
        ),
        Message(
            remoteUuid = "remote",
            direction = Transfer.Direction.Send,
            timestamp = 13,
            text = "Oh! If you've finished it, sure. Otherwise, no rush.",
        ),
        Message(
            remoteUuid = "remote",
            direction = Transfer.Direction.Receive,
            timestamp = 14,
            text = "I finished it last night. That ending was wild!",
        ),
        Message(
            remoteUuid = "remote",
            direction = Transfer.Direction.Send,
            timestamp = 15,
            text = "I told you! We definitely need to talk about it over coffee.",
        ),
        Message(
            remoteUuid = "remote",
            direction = Transfer.Direction.Send,
            timestamp = 16,
            text = "https://somerandomcaffe.com",
        ),
    ).reversed()

    val remote = Remote(
        uuid = "remote",
        displayName = "Test Device",
        userName = "user",
        hostname = "hostname",
        status = Remote.RemoteStatus.Connected,
        messages = messages,
        supportsTextMessages = true,
        isFavorite = false,
    )

    WarpinatorTheme {
        MessagesPaneContent(
            remote = remote,
            paneMode = false,
            onBack = {},
        )
    }
}