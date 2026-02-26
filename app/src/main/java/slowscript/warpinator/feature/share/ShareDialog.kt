package slowscript.warpinator.feature.share

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import slowscript.warpinator.R
import slowscript.warpinator.core.data.WarpinatorViewModel
import slowscript.warpinator.core.design.components.DynamicAvatarCircle
import slowscript.warpinator.core.design.shapes.segmentedDynamicShapes
import slowscript.warpinator.core.model.Remote
import slowscript.warpinator.core.model.Remote.RemoteStatus
import slowscript.warpinator.core.utils.RemoteDisplayInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareDialog(
    onDismiss: () -> Unit,
    uris: List<Uri>,
    text: String?,
    viewModel: WarpinatorViewModel = hiltViewModel(),
) {
    val remotes = viewModel.remoteListState.collectAsStateWithLifecycle()
    val onSendUris = { remote: Remote, uris: List<Uri> ->
        viewModel.sendUris(remote, uris, false)
        onDismiss()
    }

    val onSendText = { remote: Remote, text: String ->
        viewModel.sendTextMessage(remote, text)
        onDismiss()
    }

    BoxWithConstraints {
        if (maxWidth < 600.dp) {
            ShareDialogFullscreenWrapper(
                onDismiss = onDismiss,
                remotes = remotes.value,
                uris = uris,
                text = text,
                onSendUris = onSendUris,
                onSendText = onSendText,
            )
        } else {
            ShareDialogFloatingWrapper(
                onDismiss = onDismiss,
                remotes = remotes.value,
                uris = uris,
                text = text,
                onSendUris = onSendUris,
                onSendText = onSendText,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareDialogFullscreenWrapper(
    onDismiss: () -> Unit,
    remotes: List<Remote>,
    uris: List<Uri>,
    text: String?,
    onSendUris: (Remote, List<Uri>) -> Unit = { _: Remote, _: List<Uri> -> },
    onSendText: (Remote, String) -> Unit = { _: Remote, _: String -> },
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
        ),
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    title = {
                        Text(
                            "Share with", maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel")
                        }
                    },
                )
            },
        ) { innerPadding ->
            ShareDialogContent(
                innerPadding + PaddingValues(horizontal = 16.dp),
                remotes,
                uris,
                text,
                onSendUris,
                onSendText,
            )
        }
    }
}

@Composable
private fun ShareDialogFloatingWrapper(
    onDismiss: () -> Unit,
    remotes: List<Remote>,
    uris: List<Uri>,
    text: String?,
    onSendUris: (Remote, List<Uri>) -> Unit = { _: Remote, _: List<Uri> -> },
    onSendText: (Remote, String) -> Unit = { _: Remote, _: String -> },
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share with") },
        confirmButton = { TextButton(onDismiss) { Text("Cancel") } },
        icon = { Icon(Icons.Rounded.Share, contentDescription = null) },
        text = {
            ShareDialogContent(PaddingValues(), remotes, uris, text, onSendUris, onSendText)
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShareDialogContent(
    innerPadding: PaddingValues,
    remotes: List<Remote>,
    uris: List<Uri>,
    text: String?,
    onSendUris: (Remote, List<Uri>) -> Unit,
    onSendText: (Remote, String) -> Unit,
) {
    var editedText by rememberSaveable { mutableStateOf(text) }
    var isEditing by remember { mutableStateOf(false) }
    val textMode = uris.isEmpty() && text != null


    LazyColumn(
        contentPadding = innerPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {

            if (textMode) {
                AnimatedContent(
                    targetState = isEditing,
                    label = "TextEditAnimation",
                    transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                ) { editing ->
                    if (editing) {
                        val focusRequester = remember { FocusRequester() }
                        OutlinedTextField(
                            value = editedText.orEmpty(),
                            onValueChange = { editedText = it },
                            suffix = {
                                IconButton(onClick = { isEditing = false }) {
                                    Icon(Icons.Rounded.Check, contentDescription = "Confirm")
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                unfocusedSuffixColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                focusedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                focusedSuffixColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                unfocusedBorderColor = MaterialTheme.colorScheme.secondaryContainer,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        )

                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                        }
                    } else {
                        SegmentedListItem(
                            content = {
                                Text(
                                    editedText.orEmpty(),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text(
                                    "Tap to edit",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            onClick = { isEditing = true },
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                headlineColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                            shapes = ListItemDefaults.segmentedDynamicShapes(
                                index = 0,
                                count = 1,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Text(
                        "${uris.size} files",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (remotes.isEmpty()) {
            item {
                Icon(
                    Icons.Rounded.DevicesOther,
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    stringResource(R.string.no_devices_found),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        } else items(remotes.size) { index ->
            val remote = remotes[index]
            val isFavorite = remote.isFavorite
            val status = remote.status

            val isError = status is RemoteStatus.Error
            val isConnecting =
                status == RemoteStatus.Connecting || status == RemoteStatus.AwaitingDuplex
            val isDisconnected = status == RemoteStatus.Disconnected

            val displayInfo = remember(remote) { RemoteDisplayInfo.fromRemote(remote) }

            SegmentedListItem(
                onClick = {
                    if (textMode) {
                        onSendText(remote, editedText!!)
                    } else {
                        onSendUris(remote, uris)
                    }
                },
                shapes = ListItemDefaults.segmentedDynamicShapes(
                    index = index,
                    count = remotes.size,
                ),
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                content = {
                    Text(displayInfo.title)
                },
                supportingContent = { Text(displayInfo.subtitle) },
                leadingContent = {
                    DynamicAvatarCircle(
                        bitmap = remote.picture,
                        isFavorite = isFavorite,
                        hasError = isError,
                        isLoading = isConnecting,
                        isDisabled = isDisconnected,
                    )
                },
                trailingContent = {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                },
                modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
            )
        }
    }
}

@Preview(showBackground = true, name = "Floating - Files")
@Composable
fun PreviewFloatingFiles() {

    ShareDialogFloatingWrapper(
        onDismiss = {},
        remotes = previewRemotes,
        uris = listOf(Uri.EMPTY, Uri.EMPTY),
        text = null,
    )

}

@Preview(showBackground = true, name = "Floating - Text")
@Composable
fun PreviewFloatingText() {
    ShareDialogFloatingWrapper(
        onDismiss = {},
        remotes = previewRemotes,
        uris = emptyList(),
        text = "Hello, this is a shared text snippet!",
    )

}

@Preview(name = "Fullscreen - Files")
@Composable
fun PreviewFullscreenFiles() {
    ShareDialogFullscreenWrapper(
        onDismiss = {},
        remotes = previewRemotes,
        uris = listOf(Uri.EMPTY, Uri.EMPTY),
        text = null,
    )

}

private val previewRemotes = listOf(
    Remote(
        uuid = "remote",
        displayName = "Test Device 1",
        userName = "user",
        hostname = "hostname",
        status = RemoteStatus.Connected,
        isFavorite = true,
    ),
    Remote(
        uuid = "remote",
        displayName = "Test Device 2",
        userName = "user",
        hostname = "hostname",
        status = RemoteStatus.Connected,
        isFavorite = false,
    ),
)
