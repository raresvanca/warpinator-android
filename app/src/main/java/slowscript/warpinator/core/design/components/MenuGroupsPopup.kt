package slowscript.warpinator.core.design.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset

data class MenuAction(val title: String, val icon: ImageVector, val onClick: () -> Unit)
data class MenuGroup(val actions: List<MenuAction>)

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun MenuGroupsPopup(
    menuOpen: Boolean,
    menuGroups: List<MenuGroup>,
    groupInteractionSource: MutableInteractionSource,
    onDismiss: () -> Unit = {},
    offset: DpOffset = DpOffset.Zero,
) {
    DropdownMenuPopup(
        expanded = menuOpen,
        onDismissRequest = onDismiss,
        offset = offset,
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
                            onDismiss()
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