package slowscript.warpinator.core.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.spatial.SpatialPopup

data class MenuAction(
    val title: String,
    val trailingIcon: ImageVector? = null,
    val leadingIcon: ImageVector? = null,
    val onClick: () -> Unit,
)

data class MenuGroup(val actions: List<MenuAction>, val errorGroup: Boolean = false)

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun MenuGroupsPopup(
    menuOpen: Boolean,
    menuGroups: List<MenuGroup>,
    groupInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onDismiss: () -> Unit = {},
    offset: DpOffset = DpOffset.Zero,
    properties: PopupProperties = PopupProperties(),
    minWidth: Dp? = null,
) {
    val isSpatialUiEnabled = LocalSpatialCapabilities.current.isSpatialUiEnabled

    if (isSpatialUiEnabled) {
        if (menuOpen) {
            SpatialPopup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(offset.x.value.toInt(), offset.y.value.toInt()),
                onDismissRequest = onDismiss,
            ) {
                val expandedState = remember { MutableTransitionState(false) }
                expandedState.targetState = true

                AnimatedVisibility(
                    visibleState = expandedState,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        tonalElevation = 3.dp,
                        shadowElevation = 3.dp,
                    ) {
                        MenuContent(
                            menuGroups = menuGroups,
                            groupInteractionSource = groupInteractionSource,
                            onDismiss = onDismiss,
                            minWidth = minWidth,
                        )
                    }
                }
            }
        }
    } else {
        DropdownMenuPopup(
            expanded = menuOpen,
            onDismissRequest = onDismiss,
            offset = offset,
            properties = properties,
        ) {
            MenuContent(
                menuGroups = menuGroups,
                groupInteractionSource = groupInteractionSource,
                onDismiss = onDismiss,
                minWidth = minWidth,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun MenuContent(
    menuGroups: List<MenuGroup>,
    groupInteractionSource: MutableInteractionSource,
    onDismiss: () -> Unit,
    minWidth: Dp?,
) {
    val colors = MenuDefaults.itemColors(
        textColor = MaterialTheme.colorScheme.onSecondaryContainer,
        leadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        trailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )

    val errorColors = MenuDefaults.itemColors(
        textColor = MaterialTheme.colorScheme.onErrorContainer,
        leadingIconColor = MaterialTheme.colorScheme.onErrorContainer,
        trailingIconColor = MaterialTheme.colorScheme.onErrorContainer,
    )

    Column {
        menuGroups.forEachIndexed { index, group ->
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(index, menuGroups.size),
                interactionSource = groupInteractionSource,
                containerColor = if (group.errorGroup) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
            ) {
                group.actions.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.title) },
                        leadingIcon = action.leadingIcon?.let { leadingIcon ->
                            {
                                Icon(
                                    leadingIcon, contentDescription = null,
                                )
                            }
                        },
                        trailingIcon = action.trailingIcon?.let { trailingIcon ->
                            {
                                Icon(
                                    trailingIcon, contentDescription = null,
                                )
                            }
                        },
                        onClick = {
                            action.onClick()
                            onDismiss()
                        },
                        colors = if (group.errorGroup) errorColors else colors,
                        modifier = minWidth?.let {
                            Modifier.widthIn(min = it)
                        } ?: Modifier,
                    )
                }
            }
            if (index != menuGroups.size - 1) {
                Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            }
        }
    }
}