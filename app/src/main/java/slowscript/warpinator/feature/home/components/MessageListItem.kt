package slowscript.warpinator.feature.home.components

import android.content.ClipData
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import slowscript.warpinator.R
import slowscript.warpinator.core.design.components.ExpandableSegmentedListItem
import slowscript.warpinator.core.design.components.TooltipIconButton
import slowscript.warpinator.core.design.theme.WarpinatorTheme
import slowscript.warpinator.core.model.Message
import slowscript.warpinator.core.model.Transfer
import slowscript.warpinator.core.utils.rememberAnnotatedLinkText
import java.util.Date
import kotlin.math.abs

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MessageListItem(
    message: Message,
    expanded: Boolean,
    onExpandRequest: () -> Unit,
    // Callbacks for actions
    onClear: (Message) -> Unit = {},
    itemIndex: Int,
    itemListCount: Int,
) {
    val swipeToDismissState = rememberSwipeToDismissBoxState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val haptics = LocalHapticFeedback.current

    val isSending = message.direction == Transfer.Direction.Send

    val timeString = remember(message.timestamp) {
        DateFormat.getTimeFormat(context).format(Date(message.timestamp))
    }

    val accentColor = MaterialTheme.colorScheme.primary

    val annotatedMessage = rememberAnnotatedLinkText(message.text, accentColor)

    val onCopy: () -> Unit = {
        coroutineScope.launch {
            val clipData = ClipData.newPlainText("Message", message.text)
            clipboard.setClipEntry(clipData.toClipEntry())
        }
    }

    val onShare = {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, message.text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, null)
        context.startActivity(shareIntent)
    }

    val onDelete = {
        coroutineScope.launch {
            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
            onClear(message)
        }
    }

    val semanticCustomActions = listOf(
        CustomAccessibilityAction(stringResource(R.string.delete_message_action)) {
            onDelete()
            true
        },
        CustomAccessibilityAction(stringResource(R.string.copy_message_action)) {
            onCopy()
            true
        },
        CustomAccessibilityAction(stringResource(R.string.share_message_action)) {
            onShare()
            true
        },
    )

    val tileInteractionSource = remember { MutableInteractionSource() }
    val clearInteractionSource = remember { MutableInteractionSource() }

    val isTileHovered by tileInteractionSource.collectIsHoveredAsState()
    val isTileFocused by tileInteractionSource.collectIsFocusedAsState()
    val isButtonHovered by clearInteractionSource.collectIsHoveredAsState()
    val isButtonPressed by clearInteractionSource.collectIsPressedAsState()

    val showClearAction = isTileHovered || isTileFocused || isButtonHovered || isButtonPressed

    SwipeToDismissBox(
        state = swipeToDismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !expanded,
        backgroundContent = {
            DismissBackground(swipeToDismissState)
        },
        onDismiss = { onDelete() },
        content = {
            ExpandableSegmentedListItem(
                isExpanded = expanded,
                toggleExpand = onExpandRequest,
                content = {
                    Text(
                        text = message.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = if (isSending) Icons.Rounded.Upload else Icons.Rounded.Download,
                        contentDescription = if (isSending) stringResource(R.string.transfer_direction_send_short) else stringResource(
                            R.string.transfer_direction_receive_short,
                        ),
                    )
                },
                trailingContent = {
                    Row {
                        if (showClearAction) {
                            TooltipIconButton(
                                onClick = { onDelete() },
                                icon = Icons.Rounded.Delete,
                                description = stringResource(R.string.delete_message_action),
                                interactionSource = clearInteractionSource,
                            )
                        }
                        TooltipIconButton(
                            onClick = onCopy,
                            icon = Icons.Rounded.ContentCopy,
                            description = stringResource(R.string.copy_label),
                        )
                    }
                },
                subItemBuilder = { subItemIndex, containerColor, shape ->
                    when (subItemIndex) {
                        0 -> Surface(
                            color = containerColor,
                            shape = shape,
                        ) {
                            SelectionContainer {
                                Text(
                                    annotatedMessage,
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth(),
                                )
                            }
                        }

                        1 -> Surface(
                            color = containerColor,
                            shape = shape,
                        ) {
                            Column {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            if (isSending) stringResource(R.string.sent_message_state) else stringResource(
                                                R.string.received_message_state,
                                            ),
                                        )
                                    },
                                    supportingContent = { Text(timeString) },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }
                        }

                        2 -> Surface(
                            color = containerColor,
                            shape = shape,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                            ) {
                                Button(
                                    onClick = onCopy,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        Icons.Rounded.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize),
                                    )
                                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                    Text(stringResource(R.string.copy_label))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = onShare,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary,
                                        contentColor = MaterialTheme.colorScheme.onTertiary,
                                    ),
                                ) {
                                    Icon(
                                        Icons.Rounded.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize),
                                    )
                                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                    Text(stringResource(R.string.share_label))
                                }

                            }
                        }
                    }
                },
                subItemCount = 3,
                itemIndex = itemIndex,
                listItemCount = itemListCount,
                listItemModifier = Modifier.semantics {
                    customActions = semanticCustomActions
                },
                interactionSource = tileInteractionSource,
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
                    contentDescription = null,
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
fun MessageListItemPreview() {
    var expandedId by remember { mutableStateOf<Long?>(15) }

    val messages = listOf(
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
    )

    WarpinatorTheme {
        Scaffold { paddingValues ->
            LazyColumn(
                contentPadding = paddingValues,
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                itemsIndexed(messages) { index, message ->
                    MessageListItem(
                        message = message,
                        expanded = message.timestamp == expandedId,
                        onExpandRequest = {
                            expandedId =
                                if (message.timestamp == expandedId) null else message.timestamp
                        },
                        itemIndex = index,
                        itemListCount = messages.size,
                    )
                }
            }
        }
    }
}