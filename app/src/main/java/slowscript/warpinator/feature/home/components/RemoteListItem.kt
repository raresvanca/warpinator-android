package slowscript.warpinator.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.PreviewDynamicColors
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import slowscript.warpinator.core.design.components.DynamicAvatarCircle
import slowscript.warpinator.core.design.shapes.segmentedDynamicShapes
import slowscript.warpinator.core.design.theme.WarpinatorTheme
import slowscript.warpinator.core.model.Remote
import slowscript.warpinator.core.model.Remote.RemoteStatus
import slowscript.warpinator.core.utils.RemoteDisplayInfo
import kotlin.math.abs

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RemoteListItem(
    remote: Remote,
    onFavoriteToggle: () -> Unit, onClick: () -> Unit,
    index: Int, itemCount: Int,
) {
    val isFavorite = remote.isFavorite
    val status = remote.status

    val isError = status is RemoteStatus.Error
    val isConnecting = status == RemoteStatus.Connecting || status == RemoteStatus.AwaitingDuplex
    val isDisconnected = status == RemoteStatus.Disconnected

    val swipeToDismissState = rememberSwipeToDismissBoxState()
    val coroutineScope = rememberCoroutineScope()

    val displayInfo = remember(remote) { RemoteDisplayInfo.fromRemote(remote) }
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = ListItemDefaults.SegmentedGap),
    ) {
        SwipeToDismissBox(
            state = swipeToDismissState,
            enableDismissFromStartToEnd = false,
            backgroundContent = {
                SwipeBackground(swipeToDismissState, isFavorite)
            },
            onDismiss = {
                coroutineScope.launch {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    swipeToDismissState.reset()
                    onFavoriteToggle()
                }
            },
            content = {
                SegmentedListItem(
                    onClick = onClick,
                    shapes = ListItemDefaults.segmentedDynamicShapes(
                        index = index,
                        count = itemCount,
                    ),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
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
                )
            },
        )
    }
}

@Composable
private fun SwipeBackground(
    state: SwipeToDismissBoxState, isFavorite: Boolean,
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


    val backgroundColor = if (isFavorite) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }

    val favoriteIcon = if (isFavorite) Icons.Rounded.FavoriteBorder else Icons.Rounded.Favorite
    val favoriteIconColor =
        if (isFavorite) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer


    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = backgroundColor,
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
                    imageVector = favoriteIcon,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = favoriteIconColor,
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
@PreviewDynamicColors
@Composable
fun RemoteListItemPreview() {
    // Mock Data
    val mockConnectedRemote = Remote(
        uuid = "mockConnectedRemote",
        displayName = "Connected remote",
        userName = "user",
        hostname = "device",
        status = RemoteStatus.Connected,
        isFavorite = false,
    )

    val mockErrorRemote = Remote(
        uuid = "mockErrorRemote",
        displayName = "Error remote",
        userName = "error",
        hostname = "192.168.0.999",
        status = RemoteStatus.Error("Connection Refused"),
        isFavorite = false,
    )

    val mockConnectingRemote = Remote(
        uuid = "mockConnectingRemote",
        displayName = "Connecting remote",
        status = RemoteStatus.Connecting,
        isFavorite = false,
    )

    val mockDisconnectedRemote = Remote(
        uuid = "mockDisconnectedRemote",
        hostname = "phone",
        status = RemoteStatus.Disconnected,
        isFavorite = false,
    )

    WarpinatorTheme {
        Scaffold { scaffoldPadding ->
            Column(
                Modifier
                    .padding(scaffoldPadding)
                    .padding(top = 16.dp),
            ) {
                // Connected & Favorite
                RemoteListItem(
                    remote = mockConnectedRemote.copy(isFavorite = true),
                    onFavoriteToggle = {},
                    onClick = {}, index = 0, itemCount = 6,
                )
                // Connected
                RemoteListItem(
                    remote = mockConnectedRemote,
                    onFavoriteToggle = {},
                    onClick = {}, index = 1, itemCount = 6,
                )
                // Error
                RemoteListItem(
                    remote = mockErrorRemote,
                    onFavoriteToggle = {},
                    onClick = {}, index = 2, itemCount = 6,
                )
                // Error & Favorite
                RemoteListItem(
                    remote = mockErrorRemote.copy(isFavorite = true),
                    onFavoriteToggle = {},
                    onClick = {}, index = 3, itemCount = 6,
                )
                // Connecting (Animated)
                RemoteListItem(
                    remote = mockConnectingRemote,
                    onFavoriteToggle = {},
                    onClick = {}, index = 4, itemCount = 6,
                )
                // Disconnected
                RemoteListItem(
                    remote = mockDisconnectedRemote,
                    onFavoriteToggle = {},
                    onClick = {}, index = 5, itemCount = 6,
                )
            }
        }
    }
}