package slowscript.warpinator.feature.home.components

import android.app.Activity
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import slowscript.warpinator.app.LocalNavController
import slowscript.warpinator.core.design.components.TooltipIconButton

data class MenuAction(val title: String, val icon: ImageVector, val onClick: () -> Unit)
data class MenuGroup(val actions: List<MenuAction>)


@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeMenu(
    initiallyExpanded: Boolean = false,
    onManualConnectionClick: () -> Unit,
    onRescan: () -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val groupInteractionSource = remember { MutableInteractionSource() }

    val navController = LocalNavController.current
    val context = LocalContext.current

    val menuGroups = listOf(
        MenuGroup(
            listOf(
                MenuAction(
                    "Manual connection",
                    icon = Icons.Rounded.AddLink,
                    onClick = onManualConnectionClick,
                ),
                MenuAction(
                    "Reannounce", icon = Icons.Rounded.WifiTethering, onClick = {},
                ),
                MenuAction(
                    "Rescan", icon = Icons.Rounded.Refresh, onClick = onRescan,
                ),
                MenuAction(
                    "Connection issues",
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    onClick = {},
                ),
            ),
        ),
        MenuGroup(
            listOf(
                MenuAction(
                    "Settings",
                    icon = Icons.Rounded.Settings,
                    onClick = { navController?.navigate("settings") },
                ),
                MenuAction(
                    "Save log", icon = Icons.Rounded.Archive, onClick = {},
                ),
                MenuAction(
                    "About",
                    icon = Icons.Outlined.Info,
                    onClick = { navController?.navigate("about") },
                ),
            ),
        ),
        MenuGroup(
            listOf(
                MenuAction(
                    "Exit",
                    icon = Icons.Rounded.Close,
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
        DropdownMenuPopup(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },

            ) {
            menuGroups.forEachIndexed { index, group ->
                DropdownMenuGroup(
                    shapes = MenuDefaults.groupShape(index, menuGroups.size),
                    interactionSource = groupInteractionSource,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    group.actions.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.title) },
                            trailingIcon = {
                                Icon(
                                    action.icon, contentDescription = null,
                                )
                            },
                            onClick = {
                                action.onClick()
                                menuOpen = false
                            },
                        )
                    }

                }
                if (index != menuGroups.size - 1) {
                    Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeMenuPreview() {
    Scaffold { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            HomeMenu(true, onRescan = {}, onManualConnectionClick = {})
        }
    }
}