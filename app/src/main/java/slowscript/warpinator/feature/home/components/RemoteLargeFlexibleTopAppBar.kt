package slowscript.warpinator.feature.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import slowscript.warpinator.core.design.components.DynamicAvatarCircle
import slowscript.warpinator.core.design.theme.WarpinatorTheme
import slowscript.warpinator.core.model.Remote
import slowscript.warpinator.core.utils.RemoteDisplayInfo
import java.net.InetAddress

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RemoteLargeFlexibleTopAppBar(
    remote: Remote,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    containerColor: Color = Color.Unspecified,
    elevatedContainerColor: Color = Color.Unspecified,
    isFavoriteOverride: Boolean? = null,
) {
    val isFavorite = isFavoriteOverride ?: remote.isFavorite

    val titleFormat = RemoteDisplayInfo.fromRemote(remote)

    TwoRowsTopAppBar(
        title = { expanded ->
            if (expanded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(0.dp, 12.dp),
                ) {
                    DynamicAvatarCircle(
                        bitmap = remote.picture,
                        isFavorite = isFavorite,
                        size = 64.dp,
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                    ) {
                        Text(
                            titleFormat.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            titleFormat.subtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        titleFormat.label?.let {
                            Text(
                                it,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                DynamicAvatarCircle(bitmap = remote.picture, isFavorite = isFavorite)
                Text(titleFormat.title, modifier = Modifier.padding(8.dp))
            }
        },
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = elevatedContainerColor,
        ),
        scrollBehavior = scrollBehavior,
        modifier = modifier,
        collapsedHeight = TopAppBarDefaults.LargeAppBarCollapsedHeight,
        expandedHeight = TopAppBarDefaults.LargeFlexibleAppBarWithSubtitleExpandedHeight,
        windowInsets = TopAppBarDefaults.windowInsets,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun RemoteLargeFlexibleTopAppBarPreview() {
    val remote = Remote(
        uuid = "",
        displayName = "Test Device",
        userName = "user",
        hostname = "hostname",
        address = InetAddress.getLocalHost(),
        status = Remote.RemoteStatus.Connected,
        isFavorite = false,
    )

    WarpinatorTheme {
        RemoteLargeFlexibleTopAppBar(
            remote = remote, isFavoriteOverride = false,
            navigationIcon = {
                IconButton(onClick = {}) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack, "",
                    )
                }
            },
        )
    }
}