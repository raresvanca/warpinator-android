package slowscript.warpinator.feature.home.components

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilePresent
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.ModeComment
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TransferFloatingActionButton(
    onSendFolder: () -> Unit,
    onSendFile: () -> Unit,
    onSendMessage: () -> Unit = {},
    initiallyExpanded: Boolean = false,
) {
    var isMenuExpanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val haptics = LocalHapticFeedback.current

    BackHandler(isMenuExpanded) { isMenuExpanded = false }
    FloatingActionButtonMenu(
        expanded = isMenuExpanded,
        button = {
            ToggleFloatingActionButton(
                modifier = Modifier.semantics {
                    traversalIndex = -1f
                    stateDescription = if (isMenuExpanded) "Expanded" else "Collapsed"
                    contentDescription = "Toggle menu"
                },
                checked = isMenuExpanded,
                onCheckedChange = {
                    isMenuExpanded = !isMenuExpanded
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                },
            ) {
                val imageVector by remember {
                    derivedStateOf {
                        if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Upload
                    }
                }
                Icon(
                    painter = rememberVectorPainter(imageVector),
                    contentDescription = null,
                    modifier = Modifier.animateIcon({ checkedProgress }),
                )
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = onSendMessage,
            text = { Text("Send Message") },
            icon = { Icon(Icons.Rounded.ModeComment, contentDescription = "Send Message") },
        )
        FloatingActionButtonMenuItem(
            onClick = onSendFolder,
            text = { Text("Send Folder") },
            icon = { Icon(Icons.Rounded.Folder, contentDescription = "Send Folder") },
        )
        FloatingActionButtonMenuItem(
            onClick = onSendFile,
            text = { Text("Send File") },
            icon = { Icon(Icons.Rounded.FilePresent, contentDescription = "Send File") },
        )

    }
}

@Preview(showBackground = true)
@Composable
fun TransferFloatingActionButtonPreview() {
    Scaffold(
        floatingActionButton = {
            TransferFloatingActionButton(
                onSendFolder = {},
                onSendFile = {},
                initiallyExpanded = true,
            )
        },
    ) {}
}