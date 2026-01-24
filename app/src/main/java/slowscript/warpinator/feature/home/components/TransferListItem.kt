package slowscript.warpinator.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import slowscript.warpinator.R
import slowscript.warpinator.core.design.components.ExpandableSegmentedListItem
import slowscript.warpinator.core.design.components.TooltipIconButton
import slowscript.warpinator.core.design.theme.WarpinatorTheme
import slowscript.warpinator.core.model.Transfer
import slowscript.warpinator.feature.home.state.TransferUiActionButtons
import slowscript.warpinator.feature.home.state.TransferUiProgressIndicator
import slowscript.warpinator.feature.home.state.toUiState
import kotlin.math.abs

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TransferListItem(
    transfer: Transfer,
    expanded: Boolean,
    onExpandRequest: () -> Unit,
    // Callbacks for actions
    onItemOpen: (Transfer) -> Unit = {},
    onAccept: (Transfer) -> Unit = {},
    onDecline: (Transfer) -> Unit = {},
    onStop: (Transfer) -> Unit = {},
    onRetry: (Transfer) -> Unit = {},
    onClear: (Transfer) -> Unit = {},
    itemIndex: Int,
    itemListCount: Int,
) {
    val uiState = transfer.toUiState()
    val swipeToDismissState = rememberSwipeToDismissBoxState()
    val coroutineScope = rememberCoroutineScope()

    val haptics = LocalHapticFeedback.current

    // 2. UI COMPOSITION
    SwipeToDismissBox(
        state = swipeToDismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = uiState.allowDismiss && !expanded,
        backgroundContent = {
            DismissBackground(swipeToDismissState)
        },
        onDismiss = {
            coroutineScope.launch {
                haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                onClear(transfer)
            }
        },
        content = {
            ExpandableSegmentedListItem(
                isExpanded = expanded,
                toggleExpand = onExpandRequest,
                content = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = uiState.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(${uiState.totalSize})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                },
                supportingContent = {
                    Text(
                        text = uiState.statusText, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = if (uiState.isSending) Icons.Rounded.Upload else Icons.Rounded.Download,
                        contentDescription = if (uiState.isSending) stringResource(R.string.transfer_direction_send_short) else stringResource(
                            R.string.transfer_direction_receive_short,
                        ),
                        tint = uiState.iconColor,
                    )
                },
                trailingContent = {
                    Row {
                        when (uiState.actionButtons) {
                            TransferUiActionButtons.AcceptAndDecline -> {
                                TooltipIconButton(
                                    onClick = { onAccept(transfer) }, icon = Icons.Rounded.Check,
                                    description = "Accept",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                TooltipIconButton(
                                    onClick = { onDecline(transfer) },
                                    icon = Icons.Rounded.Close,
                                    description = "Decline",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }

                            TransferUiActionButtons.Stop -> {
                                TooltipIconButton(
                                    onClick = { onStop(transfer) },
                                    icon = Icons.Rounded.Stop,
                                    description = "Stop",
                                )

                            }

                            TransferUiActionButtons.Retry -> {
                                TooltipIconButton(
                                    description = "Retry",
                                    onClick = { onRetry(transfer) },
                                    icon = Icons.Rounded.Refresh,
                                )
                            }

                            TransferUiActionButtons.OpenFolder -> {
                                TooltipIconButton(
                                    description = "Open folder",
                                    onClick = { onItemOpen(transfer) },
                                    icon = Icons.Rounded.FolderOpen,
                                )
                            }

                            TransferUiActionButtons.Cancel -> {
                                TooltipIconButton(
                                    onClick = { onStop(transfer) },
                                    icon = Icons.Rounded.Clear,
                                    description = "Cancel",
                                )
                            }

                            TransferUiActionButtons.None -> {}
                        }
                    }
                },
                subItemBuilder = { subItemIndex, containerColor, shape ->
                    when (subItemIndex) {
                        0 -> Surface(
                            color = containerColor,
                            shape = shape,
                        ) {
                            Column {
                                // Detailed stats (Speed, Transferred/Total)
                                ListItem(
                                    headlineContent = { Text(if (uiState.isSending) "Outgoing transfer" else "Incoming transfer") },
                                    supportingContent = { Text(uiState.statusLongText) },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )

                                when (uiState.progressIndicator) {
                                    TransferUiProgressIndicator.Active -> {
                                        LinearWavyProgressIndicator(
                                            progress = { uiState.progressFloat },
                                            amplitude = { 0.5f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp, 12.dp),
                                            trackColor = MaterialTheme.colorScheme.surface,
                                        )
                                    }

                                    TransferUiProgressIndicator.Static -> {
                                        // Static progress bar for finished/failed states
                                        LinearProgressIndicator(
                                            progress = { uiState.progressFloat },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp, 12.dp),
                                            trackColor = MaterialTheme.colorScheme.surface,
                                        )
                                    }

                                    TransferUiProgressIndicator.None -> {}
                                }
                            }
                        }

                        1 -> Surface(
                            color = containerColor,
                            shape = shape,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                            ) {


                                when (uiState.actionButtons) {
                                    TransferUiActionButtons.AcceptAndDecline -> {
                                        Button(
                                            onClick = { onAccept(transfer) },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Icon(
                                                Icons.Rounded.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(ButtonDefaults.IconSize),
                                            )
                                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                            Text("Accept")
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = { onDecline(transfer) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error,
                                                contentColor = MaterialTheme.colorScheme.onError,
                                            ),
                                        ) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(ButtonDefaults.IconSize),
                                            )
                                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                            Text("Decline")
                                        }
                                    }

                                    TransferUiActionButtons.Stop -> {
                                        Button(
                                            onClick = { onStop(transfer) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondary,
                                                contentColor = MaterialTheme.colorScheme.onSecondary,
                                            ),
                                        ) {
                                            Icon(
                                                Icons.Rounded.Stop,
                                                contentDescription = null,
                                                modifier = Modifier.size(ButtonDefaults.IconSize),
                                            )
                                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                            Text("Stop")
                                        }
                                    }

                                    TransferUiActionButtons.Retry -> {
                                        Button(
                                            onClick = { onRetry(transfer) },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Icon(
                                                Icons.Rounded.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(ButtonDefaults.IconSize),
                                            )
                                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                            Text("Retry")
                                        }
                                    }

                                    TransferUiActionButtons.OpenFolder -> {
                                        Button(
                                            onClick = { onItemOpen(transfer) },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Icon(
                                                Icons.Rounded.FolderOpen,
                                                contentDescription = null,
                                                modifier = Modifier.size(ButtonDefaults.IconSize),
                                            )
                                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                            Text("Open")
                                        }
                                    }

                                    TransferUiActionButtons.Cancel -> {
                                        Button(
                                            onClick = { onStop(transfer) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondary,
                                                contentColor = MaterialTheme.colorScheme.onSecondary,
                                            ),
                                        ) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(ButtonDefaults.IconSize),
                                            )
                                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                            Text("Cancel")
                                        }
                                    }

                                    TransferUiActionButtons.None -> {}
                                }

                            }
                        }
                    }
                },
                subItemCount = if (uiState.actionButtons != TransferUiActionButtons.None) 2 else 1,
                itemIndex = itemIndex,
                listItemCount = itemListCount,
            )
        },
    )
}

@Composable
private fun DismissBackground(
    state: SwipeToDismissBoxState,
) {
    val density = LocalDensity.current
    val offsetInDp = try {
        val offset = abs(state.requireOffset())
        with(density) {
            offset.toDp()
        }
    } catch (_: IllegalStateException) {
        0f.dp
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxHeight()
                .width(offsetInDp),
        ) {
            Box(
                contentAlignment = Alignment.CenterEnd,

                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(align = Alignment.End, unbounded = true),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Remove transfer",
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier
                        .padding(end = 24.dp)
                        .requiredSize(24.dp),
                )

            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@PreviewLightDark
@Composable
fun TransferListItemPreview() {
    var expandedId by remember { mutableStateOf<String?>("2") }

    // 1. Mock Incoming Transfer (Waiting)
    val incoming = Transfer(
        remoteUuid = "remote-uuid",
        direction = Transfer.Direction.Receive,
        status = Transfer.Status.WaitingPermission,
        singleFileName = "holiday_photos.zip",
        fileCount = 1,
        totalSize = 1024 * 1024 * 5, // 5MB
    )


    // 2. Mock Outgoing Transfer (Transferring)
    val outgoing = Transfer(
        remoteUuid = "remote-uuid",
        direction = Transfer.Direction.Send,
        status = Transfer.Status.Transferring,
        fileCount = 12,
        totalSize = 1024 * 1024 * 100, // 100MB
        bytesTransferred = 1024 * 1024 * 45, // 45MB
        bytesPerSecond = 1024 * 1024 * 2, // 2MB/s
        startTime = System.currentTimeMillis() - 25000, // Started 25s ago
    )


    // 3. Mock Finished Transfer
    val finished = Transfer(
        remoteUuid = "remote-uuid",
        direction = Transfer.Direction.Send,
        status = Transfer.Status.Finished,
        singleFileName = "contract.pdf",
        fileCount = 1,
        totalSize = 1024 * 500,
        bytesTransferred = 1024 * 500,
    )

    WarpinatorTheme {
        Scaffold { paddingValues ->
            LazyColumn(
                contentPadding = paddingValues,
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                item {
                    // Incoming (Waiting)
                    TransferListItem(
                        transfer = incoming,
                        expanded = expandedId == "1",
                        onExpandRequest = { expandedId = if (expandedId == "1") null else "1" },
                        itemIndex = 0,
                        itemListCount = 3,
                    )

                }
                item {
                    // Outgoing (Progress)
                    TransferListItem(
                        transfer = outgoing,
                        expanded = expandedId == "2",
                        onExpandRequest = { expandedId = if (expandedId == "2") null else "2" },
                        itemIndex = 1,
                        itemListCount = 3,
                    )
                }
                item {
                    // Finished
                    TransferListItem(
                        transfer = finished,
                        expanded = expandedId == "3",
                        onExpandRequest = { expandedId = if (expandedId == "3") null else "3" },
                        itemIndex = 2,
                        itemListCount = 3,
                    )
                }
            }
        }
    }
}