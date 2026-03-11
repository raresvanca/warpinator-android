package slowscript.warpinator.feature.home.panes

import android.content.ClipDescription
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import slowscript.warpinator.R
import slowscript.warpinator.core.data.WarpinatorViewModel
import slowscript.warpinator.core.design.components.DragAndDropUiMode
import slowscript.warpinator.core.design.components.FileDropTargetIndicator
import slowscript.warpinator.core.design.components.TooltipIconButton
import slowscript.warpinator.core.design.components.fileDropTarget
import slowscript.warpinator.core.design.components.rememberDropTargetState
import slowscript.warpinator.core.design.components.rememberShortcutLabelText
import slowscript.warpinator.core.design.theme.WarpinatorTheme
import slowscript.warpinator.core.model.Message
import slowscript.warpinator.core.model.Remote
import slowscript.warpinator.core.model.Transfer
import slowscript.warpinator.core.notification.components.NotificationInhibitor
import slowscript.warpinator.core.utils.KeyboardShortcuts
import slowscript.warpinator.feature.home.components.MessageListItem
import slowscript.warpinator.feature.home.components.RemoteLargeFlexibleTopAppBar
import slowscript.warpinator.feature.home.components.SendMessageDialog
import slowscript.warpinator.feature.home.components.TransferFloatingActionButton
import slowscript.warpinator.feature.home.components.TransferListItem

@Composable
fun TransfersPane(
    remote: Remote,
    paneMode: Boolean,
    onBack: () -> Unit,
    onOpenMessagesPane: () -> Unit,
    onFavoriteToggle: (Remote) -> Unit,
    viewModel: WarpinatorViewModel = hiltViewModel(),
) {
    NotificationInhibitor(
        remoteUuid = remote.uuid,
        transfers = true,
        messages = viewModel.integrateMessages,
    )

    TransferPaneContent(
        remote = remote,
        paneMode = paneMode,
        integrateMessages = viewModel.integrateMessages,
        onBack = onBack,
        onOpenMessagesPane = onOpenMessagesPane,
        onSendMessage = viewModel::sendTextMessage,
        onFavoriteToggle = onFavoriteToggle,
        onAcceptTransfer = viewModel::acceptTransfer,
        onDeclineTransfer = viewModel::declineTransfer,
        onStopTransfer = viewModel::cancelTransfer,
        onRetryTransfer = viewModel::retryTransfer,
        onItemOpen = viewModel::openTransfer,
        onSendUris = { uris, isDir ->
            viewModel.sendUris(remote, uris, isDir)
        },
        onClearTransfer = viewModel::clearTransfer,
        onClearMessage = viewModel::clearMessage,
        onClearTransfers = viewModel::clearAllFinished,
        onReconnect = viewModel::reconnect,
    )
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TransferPaneContent(
    remote: Remote,
    paneMode: Boolean,
    integrateMessages: Boolean,
    onBack: () -> Unit = {},
    onOpenMessagesPane: () -> Unit = {},
    onSendMessage: (Remote, String) -> Unit = { _: Remote, _: String -> },
    isFavoriteOverride: Boolean? = null,
    onFavoriteToggle: (Remote) -> Unit = {},
    onSendUris: (List<Uri>, Boolean) -> Unit = { _: List<Uri>, _: Boolean -> },
    onAcceptTransfer: (Transfer) -> Unit = {},
    onDeclineTransfer: (Transfer) -> Unit = {},
    onStopTransfer: (Transfer) -> Unit = {},
    onRetryTransfer: (Transfer) -> Unit = {},
    onItemOpen: (Transfer) -> Unit = {},
    onClearTransfer: (Transfer) -> Unit = {},
    onClearMessage: (Message) -> Unit = {},
    onClearTransfers: (String) -> Unit = {},
    onReconnect: (Remote) -> Unit = {},
) {
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        onSendUris(uris, false)
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            onSendUris(listOf(uri), true)
        }
    }

    val fileDropTargetState = rememberDropTargetState(
        onUrisDropped = { uris ->
            onSendUris(uris, false)
            true
        },
        shouldStartDragAndDrop = shouldStartDragAndDrop@{ event ->
            val description =
                event.toAndroidDragEvent().clipDescription ?: return@shouldStartDragAndDrop false
            (0 until description.mimeTypeCount).any { mimeType ->
                description.getMimeType(mimeType) !in setOf(
                    ClipDescription.MIMETYPE_TEXT_PLAIN,
                    ClipDescription.MIMETYPE_TEXT_HTML,
                    ClipDescription.MIMETYPE_TEXT_INTENT,
                )
            }
        },
    )

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var showMessageDialog by rememberSaveable { mutableStateOf(false) }

    KeyboardShortcuts { event ->
        when {
            event.isCtrlPressed && event.key == Key.One || event.isCtrlPressed && event.key == Key.O -> {
                filePicker.launch(arrayOf("*/*"))
                true
            }

            event.isCtrlPressed && event.key == Key.Two || event.isCtrlPressed && event.isShiftPressed && event.key == Key.O -> {
                folderPicker.launch(null)
                true
            }

            event.isCtrlPressed && event.key == Key.Three || event.isCtrlPressed && event.key == Key.M -> {
                if (integrateMessages) showMessageDialog = true
                else onOpenMessagesPane()

                true
            }

            event.isCtrlPressed && event.key == Key.D -> {
                onFavoriteToggle(remote)
                true
            }

            event.isCtrlPressed && event.isShiftPressed && event.key == Key.R -> {
                onReconnect(remote)
                true
            }

            event.isCtrlPressed && event.isShiftPressed && event.key == Key.Delete -> {
                onClearTransfers(remote.uuid)
                true
            }

            else -> false
        }
    }

    Scaffold(
        topBar = {
            RemoteLargeFlexibleTopAppBar(
                remote = remote,
                navigationIcon = {
                    if (!paneMode) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back_button_label),
                            )
                        }
                    }
                },
                isFavoriteOverride = isFavoriteOverride,
                actions = {
                    TooltipIconButton(
                        onClick = {
                            onClearTransfers(remote.uuid)
                        },
                        icon = Icons.Rounded.ClearAll,
                        description = rememberShortcutLabelText(
                            KeyEvent.KEYCODE_DEL, ctrl = true, shift = true,
                            text = stringResource(R.string.clear_transfer_history_label),
                        ),
                    )

                    val favouriteButtonSemanticState = if (isFavoriteOverride
                            ?: remote.isFavorite
                    ) stringResource(R.string.favorite_label) else stringResource(R.string.not_favorite_label)

                    TooltipIconButton(
                        onClick = { onFavoriteToggle(remote) },
                        icon = if (isFavoriteOverride
                                ?: remote.isFavorite
                        ) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        description = rememberShortcutLabelText(
                            KeyEvent.KEYCODE_D, ctrl = true,
                            text = if (isFavoriteOverride
                                    ?: remote.isFavorite
                            ) stringResource(R.string.remove_from_favorites_label) else stringResource(
                                R.string.add_to_favorites_label,
                            ),
                        ),
                        modifier = Modifier.semantics {
                            stateDescription = favouriteButtonSemanticState

                            toggleableState = if (isFavoriteOverride
                                    ?: remote.isFavorite
                            ) ToggleableState.On else ToggleableState.Off

                        },
                    )

                    if (!integrateMessages) TooltipIconButton(
                        onClick = onOpenMessagesPane,
                        icon = Icons.AutoMirrored.Rounded.Message,
                        description = rememberShortcutLabelText(
                            keyCode = KeyEvent.KEYCODE_M, ctrl = true,
                            text = stringResource(R.string.messages),
                        ),
                        enabled = remote.supportsTextMessages,
                        addBadge = remote.hasUnreadMessages,
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (remote.status == Remote.RemoteStatus.Connected) {
                TransferFloatingActionButton(
                    onSendFile = { filePicker.launch(arrayOf("*/*")) },
                    onSendFolder = { folderPicker.launch(null) },
                    onSendMessage = {
                        if (integrateMessages) showMessageDialog = true
                        else onOpenMessagesPane()
                    },
                )
            }
        },
    ) { padding ->
        val transfers = remote.transfers
        var expandedTransferID by rememberSaveable { mutableStateOf<String?>(null) }

        val listContentDescription =
            stringResource(R.string.transfers_history_list_content_description)

        LazyColumn(
            contentPadding = padding.plus(
                PaddingValues(
                    bottom = 100.dp, start = 16.dp, end = 16.dp,
                ),
            ),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .semantics {
                    contentDescription = listContentDescription
                }
                .fileDropTarget(fileDropTargetState),
        ) {

            item {
                ConnectionStatusCard(
                    remote.status,
                    transfers.size,
                    if (integrateMessages) remote.messages.size else null,
                ) { onReconnect(remote) }
            }

            if (fileDropTargetState.uiMode != DragAndDropUiMode.None) {
                item {
                    FileDropTargetIndicator(
                        fileDropTargetState.uiMode,
                        text = stringResource(R.string.drop_here_to_send),
                        modifier = Modifier.fillParentMaxSize(),
                    )
                }
                return@LazyColumn
            }

            if ((transfers.isEmpty() && !integrateMessages) || (transfers.isEmpty() && remote.messages.isEmpty())) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .height(400.dp)
                            .fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Rounded.Inbox,
                            tint = MaterialTheme.colorScheme.primary,
                            contentDescription = null,
                            modifier = Modifier.size(100.dp),
                        )
                        Text(
                            stringResource(R.string.no_transfers_yet),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            if (integrateMessages && remote.messages.isNotEmpty()) {
                itemsIndexed(
                    remote.messages,
                    key = { _, message -> message.timestamp },
                ) { index, message ->
                    val expanded = "mt${message.timestamp}" == expandedTransferID

                    MessageListItem(
                        message = message,
                        expanded = expanded,
                        onExpandRequest = {
                            expandedTransferID = if (expanded) null else "mt${message.timestamp}"
                        },
                        onClear = {
                            onClearMessage(message)
                        },
                        itemIndex = index,
                        itemListCount = remote.messages.size,
                    )

                    Spacer(modifier = Modifier.height(ListItemDefaults.SegmentedGap))
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            itemsIndexed(
                transfers,
                key = { _, transfer -> transfer.uid },
            ) { index, transfer ->
                val expanded = transfer.uid == expandedTransferID
                TransferListItem(
                    transfer = transfer,
                    expanded = expanded,
                    onExpandRequest = {
                        expandedTransferID = if (expanded) null else transfer.uid
                    },
                    onAccept = onAcceptTransfer,
                    onDecline = onDeclineTransfer,
                    onStop = onStopTransfer,
                    onRetry = onRetryTransfer,
                    onItemOpen = onItemOpen,
                    onClear = onClearTransfer,
                    itemIndex = index,
                    itemListCount = transfers.size,
                )

                Spacer(modifier = Modifier.height(ListItemDefaults.SegmentedGap))
            }
        }
    }

    if (showMessageDialog) {
        SendMessageDialog(
            onSendMessage = { message ->
                onSendMessage(remote, message)
            },
            onDismiss = {
                showMessageDialog = false
            },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ConnectionStatusCard(
    status: Remote.RemoteStatus, transfersCount: Int, messageCount: Int? = null,
    onReconnect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .padding(vertical = 16.dp)
            .fillMaxWidth()
            .semantics(true) {
                liveRegion = LiveRegionMode.Polite
            },
        colors = if (status is Remote.RemoteStatus.Error) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) else CardDefaults.cardColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            // TODO(raresvanca): look at putting shape backgrounds for the status icons
            when (status) {
                Remote.RemoteStatus.AwaitingDuplex, Remote.RemoteStatus.Connecting -> {
                    LoadingIndicator(modifier = Modifier.padding(horizontal = 6.dp))
                }

                Remote.RemoteStatus.Connected -> {
                    Icon(
                        Icons.Rounded.SyncAlt,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .size(28.dp),
                    )
                }

                Remote.RemoteStatus.Disconnected -> {
                    Icon(
                        Icons.Rounded.SyncAlt,
                        null,
                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .size(28.dp),
                    )
                }

                is Remote.RemoteStatus.Error -> {
                    Icon(
                        Icons.Rounded.Error,
                        null,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .size(28.dp),
                    )
                }
            }


            Column {
                Text(
                    when (status) {
                        Remote.RemoteStatus.AwaitingDuplex -> stringResource(R.string.remote_awaiting_duplex)
                        Remote.RemoteStatus.Connecting -> stringResource(R.string.remote_connecting)
                        Remote.RemoteStatus.Connected -> stringResource(R.string.remote_connected)
                        Remote.RemoteStatus.Disconnected -> stringResource(R.string.remote_disconnected)
                        is Remote.RemoteStatus.Error -> stringResource(
                            R.string.remote_failed_to_connect,
                            status.message,
                        )
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    buildString {
                        append(
                            pluralStringResource(
                                R.plurals.transfers_count,
                                transfersCount,
                                transfersCount,
                            ),
                        )
                        if (messageCount != null) {
                            append(" • ")
                            append(
                                pluralStringResource(
                                    R.plurals.messages_count,
                                    messageCount,
                                    messageCount,
                                ),
                            )
                        }
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = LocalContentColor.current.copy(alpha = 0.8f),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (status == Remote.RemoteStatus.Disconnected || status is Remote.RemoteStatus.Error) {
                FilledTonalButton(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    colors = if (status is Remote.RemoteStatus.Error) ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ) else ButtonDefaults.filledTonalButtonColors(),
                    onClick = {
                        onReconnect()
                    },
                ) {
                    Text(stringResource(R.string.reconnect_button_label))
                }
            }
        }
    }
}


@Preview
@Composable
private fun TransfersPanePreview() {
    // Transfers covering different states and directions
    val transfers = listOf(
        Transfer(
            remoteUuid = "remote",
            direction = Transfer.Direction.Send,
            status = Transfer.Status.Transferring,
            totalSize = 100 * 1024 * 1024, // 100 MB
            bytesTransferred = 45 * 1024 * 1024, // 45 MB
            singleFileName = "sending_video.mp4",
            fileCount = 1,
        ),
        Transfer(
            remoteUuid = "remote",
            direction = Transfer.Direction.Receive,
            status = Transfer.Status.Transferring,
            totalSize = 50 * 1024 * 1024,
            bytesTransferred = 10 * 1024 * 1024,
            singleFileName = "receiving_document.pdf",
            fileCount = 1,
        ),
        Transfer(
            remoteUuid = "remote",
            direction = Transfer.Direction.Receive,
            status = Transfer.Status.WaitingPermission,
            totalSize = 2 * 1024 * 1024,
            singleFileName = "incoming_request.zip",
            fileCount = 1,
        ),
        Transfer(
            remoteUuid = "remote",
            direction = Transfer.Direction.Send,
            status = Transfer.Status.Finished,
            totalSize = 5 * 1024 * 1024,
            bytesTransferred = 5 * 1024 * 1024,
            singleFileName = "sent_image.jpg",
            fileCount = 1,
        ),
        Transfer(
            remoteUuid = "remote",
            direction = Transfer.Direction.Receive,
            status = Transfer.Status.Finished,
            totalSize = 3 * 1024 * 1024,
            bytesTransferred = 3 * 1024 * 1024,
            singleFileName = "received_song.mp3",
            fileCount = 1,
        ),
        Transfer(
            remoteUuid = "remote",
            direction = Transfer.Direction.Receive,
            status = Transfer.Status.Declined,
            totalSize = 1024,
            singleFileName = "declined_file.exe",
            fileCount = 1,
        ),
        Transfer(
            remoteUuid = "remote",
            direction = Transfer.Direction.Send,
            status = Transfer.Status.Failed(
                error = Transfer.Error.Generic("Network error"), isRecoverable = true,
            ),
            totalSize = 10 * 1024 * 1024,
            bytesTransferred = 1 * 1024 * 1024,
            singleFileName = "failed_upload.iso",
            fileCount = 1,
        ),
        Transfer(
            remoteUuid = "remote",
            direction = Transfer.Direction.Receive,
            status = Transfer.Status.Failed(
                error = Transfer.Error.StorageFull, isRecoverable = false,
            ),
            totalSize = 500 * 1024,
            singleFileName = "corrupted_file.bin",
            fileCount = 1,
        ),
        Transfer(
            remoteUuid = "remote",
            direction = Transfer.Direction.Send,
            status = Transfer.Status.Paused,
            totalSize = 200 * 1024 * 1024,
            bytesTransferred = 100 * 1024 * 1024,
            singleFileName = "paused_backup.tar.gz",
            fileCount = 1,
        ),
        Transfer(
            remoteUuid = "remote",
            direction = Transfer.Direction.Receive,
            status = Transfer.Status.Stopped,
            totalSize = 15 * 1024 * 1024,
            bytesTransferred = 2 * 1024 * 1024,
            singleFileName = "stopped_download.apk",
            fileCount = 1,
        ),
        Transfer(
            remoteUuid = "remote",
            direction = Transfer.Direction.Send,
            status = Transfer.Status.Failed(
                error = Transfer.Error.FileNotFound("missing_file.txt"), isRecoverable = false,
            ),
            totalSize = 0,
            singleFileName = "missing_file.txt",
            fileCount = 1,
        ),
        Transfer(
            remoteUuid = "remote",
            direction = Transfer.Direction.Receive,
            status = Transfer.Status.FinishedWithErrors(
                errors = listOf(Transfer.Error.PermissionDenied("/root/forbidden")),
            ),
            totalSize = 8 * 1024 * 1024,
            bytesTransferred = 8 * 1024 * 1024,
            singleFileName = "completed_with_errors.log",
            fileCount = 1,
        ),
        Transfer(
            remoteUuid = "remote",
            direction = Transfer.Direction.Send,
            status = Transfer.Status.Initializing,
            totalSize = 0,
            singleFileName = "initializing_folder",
            fileCount = 5,
        ),
    )
    // Reverse transfers so it can be shown as in the list, because the UI flips  the list to show first the
    // added last
    val remote = Remote(
        uuid = "remote",
        displayName = "Test Device",
        userName = "user",
        hostname = "hostname",
        status = Remote.RemoteStatus.Connected,
        transfers = transfers,
        isFavorite = false,
        supportsTextMessages = true,
        hasUnreadMessages = true,
    )

    WarpinatorTheme {
        TransferPaneContent(
            remote = remote,
            paneMode = false,
            integrateMessages = false,
            onBack = {},
            isFavoriteOverride = false,
        )
    }
}


@Preview
@Composable
private fun TransfersPaneEmptyPreview() {

    // Reverse transfers so it can be shown as in the list, because the UI flips  the list to show first the
    // added last
    val remote = Remote(
        uuid = "remote",
        displayName = "Test Device",
        userName = "user",
        hostname = "hostname",
        status = Remote.RemoteStatus.Connected,
        transfers = listOf(),
        isFavorite = false,
    )

    WarpinatorTheme {
        TransferPaneContent(
            remote = remote,
            paneMode = false,
            integrateMessages = false,
            onBack = {},
            isFavoriteOverride = false,
        )
    }
}

@PreviewLightDark
@Composable
private fun ConnectionStatusCardPreview() {
    WarpinatorTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ConnectionStatusCard(
                status = Remote.RemoteStatus.Connecting, transfersCount = 3,
            ) {}
            ConnectionStatusCard(
                status = Remote.RemoteStatus.Connected, transfersCount = 5,
            ) {}
            ConnectionStatusCard(
                status = Remote.RemoteStatus.Disconnected, transfersCount = 5,
            ) {}
            ConnectionStatusCard(
                status = Remote.RemoteStatus.Error(message = "Connection failed"),
                transfersCount = 2,
            ) {}
        }
    }
}
