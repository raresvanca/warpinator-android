package slowscript.warpinator.feature.home.components

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import slowscript.warpinator.app.LocalNavController
import slowscript.warpinator.core.design.components.MenuAction
import slowscript.warpinator.core.design.components.MenuGroup
import slowscript.warpinator.core.design.components.MenuGroupsPopup
import slowscript.warpinator.core.design.components.TooltipIconButton


@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeMenu(
    initiallyExpanded: Boolean = false,
    onManualConnectionClick: () -> Unit,
    onRescan: () -> Unit,
    onReannounce: () -> Unit,
    onSaveLog: (uri: Uri) -> Unit,
) {
    val helpUrl = "https://slowscript.xyz/warpinator-android/connection-issues/"
    val uriHandler = LocalUriHandler.current

    val saveLocationPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            mimeType = "text/plain",
        ),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        onSaveLog(uri)
    }

    var menuOpen by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val groupInteractionSource = remember { MutableInteractionSource() }

    val navController = LocalNavController.current
    val context = LocalContext.current

    val menuGroups = listOf(
        MenuGroup(
            listOf(
                MenuAction(
                    "Manual connection",
                    trailingIcon = Icons.Rounded.AddLink,
                    onClick = onManualConnectionClick,
                ),
                MenuAction(
                    "Reannounce",
                    trailingIcon = Icons.Rounded.WifiTethering,
                    onClick = onReannounce,
                ),
                MenuAction(
                    "Rescan", trailingIcon = Icons.Rounded.Refresh, onClick = onRescan,
                ),
                MenuAction(
                    "Connection issues",
                    trailingIcon = Icons.AutoMirrored.Rounded.MenuBook,
                    onClick = {
                        uriHandler.openUri(helpUrl)
                    },
                ),
            ),
        ),
        MenuGroup(
            listOf(
                MenuAction(
                    "Settings",
                    trailingIcon = Icons.Rounded.Settings,
                    onClick = { navController?.navigate("settings") },
                ),
                MenuAction(
                    "Save log", trailingIcon = Icons.Rounded.Archive,
                    onClick = {
                        saveLocationPicker.launch("warpinator-log.txt")
                    },
                ),
                MenuAction(
                    "About",
                    trailingIcon = Icons.Outlined.Info,
                    onClick = { navController?.navigate("about") },
                ),
            ),
        ),
        MenuGroup(
            listOf(
                MenuAction(
                    "Exit",
                    trailingIcon = Icons.Rounded.Close,
                    onClick = {
                        (context as? Activity)?.finish()
                    },
                ),
            ),
        ),
    )

    Box(
        modifier = Modifier.wrapContentSize(Alignment.TopEnd),
    ) {
        TooltipIconButton(
            description = "Open menu",
            icon = Icons.Rounded.MoreVert,
            onClick = { menuOpen = true },
        )
        MenuGroupsPopup(
            menuGroups = menuGroups,
            groupInteractionSource = groupInteractionSource,
            menuOpen = menuOpen,
            onDismiss = { menuOpen = false },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeMenuPreview() {
    Scaffold { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            HomeMenu(
                true,
                onRescan = {},
                onManualConnectionClick = {},
                onSaveLog = {},
                onReannounce = {},
            )
        }
    }
}