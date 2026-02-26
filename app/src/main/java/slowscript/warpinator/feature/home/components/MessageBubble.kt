package slowscript.warpinator.feature.home.components

import android.content.ClipData
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import slowscript.warpinator.core.design.theme.WarpinatorTheme
import slowscript.warpinator.core.model.Message
import slowscript.warpinator.core.model.Transfer
import java.util.Date

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MessageBubble(message: Message) {
    val isSent = message.direction == Transfer.Direction.Send
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    var showMenu by remember { mutableStateOf(false) }
    var showTimestamp by remember { mutableStateOf(false) }

    val backgroundColor =
        if (isSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
    val textColor =
        if (isSent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor =
        if (isSent) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.primary
    val bubbleShape = if (isSent) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }


    val timeString = remember(message.timestamp) {
        android.text.format.DateFormat.getTimeFormat(context).format(Date(message.timestamp))
    }

    val annotatedMessage = remember(message.text) {
        buildAnnotatedString {
            // Catch urls
            val urlRegex = "(https?://[a-zA-Z0-9./_?&=-]+)".toRegex()
            var lastIndex = 0

            urlRegex.findAll(message.text).forEach { match ->
                append(message.text.substring(lastIndex, match.range.first))

                withLink(
                    LinkAnnotation.Url(
                        url = match.value,
                        styles = TextLinkStyles(

                            style = SpanStyle(
                                textDecoration = TextDecoration.Underline,
                                color = accentColor,
                            ),
                        ),
                    ),
                ) {
                    append(match.value)
                }

                lastIndex = match.range.last + 1
            }
            append(message.text.substring(lastIndex))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(
                onClick = { showTimestamp = !showTimestamp },
                onLongClick = { showMenu = true },
            ),
        horizontalAlignment = if (isSent) Alignment.End else Alignment.Start,
    ) {
        Box {
            Surface(
                color = backgroundColor,
                shape = bubbleShape,
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .padding(
                        if (isSent) PaddingValues(
                            start = 48.dp,
                            end = 16.dp,
                        ) else PaddingValues(end = 48.dp, start = 16.dp),
                    ),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        text = annotatedMessage,
                        style = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                    )
                    AnimatedVisibility(
                        visible = showTimestamp,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(
                            text = timeString,
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            DropdownMenuPopup(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {

                DropdownMenuGroup(
                    shapes = MenuDefaults.groupShape(0, 1),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.widthIn(min = 160.dp),
                ) {
                    DropdownMenuItem(
                        text = { Text("Copy") },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = "Copy",
                            )
                        },
                        onClick = {
                            coroutineScope.launch {
                                val clipData = ClipData.newPlainText("Message", message.text)
                                clipboard.setClipEntry(clipData.toClipEntry())
                            }
                            showMenu = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = "Share") },
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, message.text)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                            showMenu = false
                        },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MessageBubbleSentPreview() {
    WarpinatorTheme {
        MessageBubble(
            message = Message(
                remoteUuid = "remote",
                direction = Transfer.Direction.Send,
                timestamp = System.currentTimeMillis(),
                text = "This is a sent message with a link: https://www.google.com",
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MessageBubbleReceivedPreview() {
    WarpinatorTheme {
        MessageBubble(
            message = Message(
                remoteUuid = "remote",
                direction = Transfer.Direction.Receive,
                timestamp = System.currentTimeMillis(),
                text = "This is a received message with a link: https://www.google.com",
            ),
        )
    }
}
