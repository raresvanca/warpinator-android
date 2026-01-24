package slowscript.warpinator.feature.home.panes

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PortableWifiOff
import androidx.compose.material.icons.rounded.SignalWifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.LoadingIndicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import slowscript.warpinator.R
import slowscript.warpinator.core.data.ServiceState
import slowscript.warpinator.core.data.WarpinatorViewModel
import slowscript.warpinator.core.design.shapes.WarpinatorRoundedIconOutlineShape
import slowscript.warpinator.core.design.theme.WarpinatorTheme
import slowscript.warpinator.core.model.Remote
import slowscript.warpinator.feature.connect_dialog.ManualConnectionDialog
import slowscript.warpinator.feature.home.components.HomeMenu
import slowscript.warpinator.feature.home.components.RemoteListItem


private enum class RemoteListUiState {
    Normal, Empty, Starting, Stopping, FailedToStart, NetworkChangeRestart
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
fun RemoteListPane(
    paneMode: Boolean,
    onRemoteClick: (Remote) -> Unit,
    onFavoriteToggle: (Remote) -> Unit,
    viewModel: WarpinatorViewModel = hiltViewModel(),
) {
    val currentRemotes by viewModel.remoteListState.collectAsStateWithLifecycle()
    val currentServiceState by viewModel.serviceState.collectAsStateWithLifecycle()
    val currentNetworkState by viewModel.networkState.collectAsStateWithLifecycle()
    val currentIsRefreshing by viewModel.refreshState.collectAsStateWithLifecycle()


    // Dialog states
    var showManualConnectionDialog by remember { mutableStateOf(false) }

    if (paneMode) {

        Scaffold { innerPadding ->
            val layoutDirection = LocalLayoutDirection.current

            val padding = PaddingValues(
                end = innerPadding.calculateEndPadding(layoutDirection),
            )

            Box(
                modifier = Modifier.consumeWindowInsets(padding),
            ) {
                RemoteListPaneContent(
                    remotes = currentRemotes,
                    onRemoteClick = onRemoteClick,
                    onFavoriteToggle = onFavoriteToggle,
                    state = currentServiceState,
                    isOnline = currentNetworkState.isOnline,
                    isRefreshing = currentIsRefreshing,
                    paneMode = true,
                    onRescan = viewModel::rescan,
                )
            }
        }
    } else {
        RemoteListPaneContent(
            remotes = currentRemotes,
            onRemoteClick = onRemoteClick,
            onFavoriteToggle = onFavoriteToggle,
            state = currentServiceState,
            isOnline = currentNetworkState.isOnline,
            isRefreshing = currentIsRefreshing,
            paneMode = false,
            onRescan = viewModel::rescan,
        )
    }

    ManualConnectionDialog(showDialog = showManualConnectionDialog)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RemoteListPaneContent(
    remotes: List<Remote>,
    state: ServiceState,
    isOnline: Boolean = true,
    isRefreshing: Boolean = false,
    onRemoteClick: (Remote) -> Unit,
    onFavoriteToggle: (Remote) -> Unit,
    onRescan: () -> Unit,
    paneMode: Boolean = false,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { !paneMode },
    )
    val refreshState = rememberPullToRefreshState()

    // Dialog states
    var showManualConnectionDialog by remember { mutableStateOf(false) }
    // UIStates
    val uiState: RemoteListUiState = (when {
        state is ServiceState.Starting -> RemoteListUiState.Starting
        state is ServiceState.Stopping -> RemoteListUiState.Stopping
        state is ServiceState.InitializationFailed -> RemoteListUiState.FailedToStart
        state is ServiceState.NetworkChangeRestart -> RemoteListUiState.NetworkChangeRestart
        remotes.isEmpty() -> RemoteListUiState.Empty
        else -> RemoteListUiState.Normal
    })

    Scaffold(
        topBar = {
            if (paneMode) TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    HomeMenu(
                        onManualConnectionClick = { showManualConnectionDialog = true },
                        onRescan = onRescan,
                    )
                },
                scrollBehavior = scrollBehavior,
            ) else LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    HomeMenu(
                        onManualConnectionClick = { showManualConnectionDialog = true },
                        onRescan = onRescan,
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRescan,
            state = refreshState,
            indicator = {
                LoadingIndicator(
                    state = refreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(padding),
                )
            },
        ) {
            Crossfade(
                targetState = uiState,
                label = "RemoteListPaneContent",
                modifier = Modifier.fillMaxSize(),
            ) { listUiState ->
                LazyColumn(
                    contentPadding = padding,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                ) {
                    if (!isOnline) {
                        item {
                            Card(
                                modifier = Modifier.padding(
                                    16.dp, 12.dp,
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Rounded.SignalWifiOff, contentDescription = null)
                                    Spacer(modifier = Modifier.size(16.dp))
                                    Column {
                                        Text(
                                            "No Internet Connection",
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                        Text(
                                            "Connect via WiFi, LAN, or Hotspot, then restart the app.",
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            }
                        }
                    }


                    if (listUiState != RemoteListUiState.Normal) {
                        item {
                            Column(
                                modifier = Modifier.fillParentMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                when (listUiState) {
                                    RemoteListUiState.Empty -> {
                                        Icon(
                                            Icons.Rounded.DevicesOther,
                                            tint = MaterialTheme.colorScheme.primary,
                                            contentDescription = null,
                                            modifier = Modifier.size(150.dp),
                                        )
                                        Text(
                                            stringResource(R.string.no_devices_found),
                                            style = MaterialTheme.typography.titleLarge,
                                            modifier = Modifier.padding(32.dp),
                                        )
                                        Button(
                                            onClick = { showManualConnectionDialog = true },

                                            ) {
                                            Text(stringResource(R.string.manual_connection))
                                        }
                                    }

                                    RemoteListUiState.Starting, RemoteListUiState.NetworkChangeRestart -> {
//                                      // Using the new indeterminate loading indicator
                                        LoadingIndicator(
                                            modifier = Modifier.size(200.dp),
                                            polygons = listOf(
                                                // Some shapes to represent Warpinator, sending files and starting
                                                WarpinatorRoundedIconOutlineShape,
                                                MaterialShapes.Arrow,
                                                MaterialShapes.Ghostish,
                                                MaterialShapes.Slanted,
                                                MaterialShapes.ClamShell,
                                            ),
                                        )
                                        Text(
                                            if (listUiState == RemoteListUiState.NetworkChangeRestart) "Restarting Warpinator..." else "Starting Warpinator...",
                                            style = MaterialTheme.typography.titleLarge,
                                            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                                        )
                                        if (listUiState == RemoteListUiState.NetworkChangeRestart) Text(
                                            "Network changed",
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }

                                    RemoteListUiState.Stopping -> {
                                        Icon(
                                            Icons.Rounded.PortableWifiOff,
                                            tint = MaterialTheme.colorScheme.primary,
                                            contentDescription = null,
                                            modifier = Modifier.size(150.dp),
                                        )
                                        Text(
                                            "Stopping Warpinator...",
                                            style = MaterialTheme.typography.titleLarge,
                                            modifier = Modifier.padding(24.dp),
                                        )
                                    }

                                    RemoteListUiState.FailedToStart -> {
                                        val state = state as ServiceState.InitializationFailed


                                        Icon(
                                            Icons.Rounded.ErrorOutline,
                                            tint = MaterialTheme.colorScheme.error,
                                            contentDescription = null,
                                            modifier = Modifier.size(150.dp),
                                        )
                                        Text(
                                            "Failed to start Warpinator",
                                            style = MaterialTheme.typography.titleLarge,
                                            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),

                                            )
                                        if (state.interfaces != null) {
                                            Text(
                                                state.interfaces,
                                                style = MaterialTheme.typography.labelLarge,
                                            )
                                        }
                                        Text(
                                            state.exception,
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }

                                    else -> {}
                                }
                            }
                        }
                        return@LazyColumn
                    }

                    item {
                        Text(
                            stringResource(R.string.available_devices),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(horizontal = 26.dp)
                                .padding(
                                    top = 32.dp, bottom = 12.dp,
                                ),
                        )
                    }

                    items(remotes.size, key = { remotes[it].uuid }) { index ->
                        Box {
                            val remote = remotes[index]
                            RemoteListItem(
                                remote = remote,
                                onFavoriteToggle = { onFavoriteToggle(remote) },
                                onClick = { onRemoteClick(remote) },
                                index = index,
                                itemCount = remotes.size,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showManualConnectionDialog) ManualConnectionDialog(
        onDismiss = { showManualConnectionDialog = false },
    )
}

@Preview
@Composable
private fun RemotePanePreview() {
    val remote = Remote(
        uuid = "remote",
        displayName = "Test Device",
        userName = "user",
        hostname = "hostname",
        status = Remote.RemoteStatus.Connected,
        isFavorite = false,
    )


    WarpinatorTheme {
        RemoteListPaneContent(
            remotes = listOf(remote),
            onRemoteClick = {},
            onFavoriteToggle = {},
            onRescan = {},
            state = ServiceState.Ok,
        )
    }
}

@Preview
@Composable
private fun RemotePaneEmptyPreview() {

    WarpinatorTheme {
        RemoteListPaneContent(
            remotes = listOf(),
            onRemoteClick = {},
            onFavoriteToggle = {},
            onRescan = {},
            state = ServiceState.Ok,
        )
    }
}

@Preview
@Composable
private fun RemotePaneStartingServicePreview() {

    WarpinatorTheme {
        RemoteListPaneContent(
            remotes = listOf(),
            state = ServiceState.Starting,
            onRemoteClick = {},
            onFavoriteToggle = {},
            onRescan = {},
        )
    }
}

@Preview
@Composable
private fun RemotePaneStoppingServicePreview() {
    WarpinatorTheme {
        RemoteListPaneContent(
            remotes = listOf(),
            state = ServiceState.Stopping,
            onRemoteClick = {},
            onFavoriteToggle = {},
            onRescan = {},
        )
    }
}

@Preview
@Composable
private fun RemotePaneNetworkChangeRestartPreview() {
    WarpinatorTheme {
        RemoteListPaneContent(
            remotes = listOf(),
            state = ServiceState.NetworkChangeRestart,
            onRemoteClick = {},
            onFavoriteToggle = {},
            onRescan = {},
        )
    }
}

@Preview
@Composable
private fun RemotePaneInitializationFailedPreview() {
    WarpinatorTheme {
        RemoteListPaneContent(
            remotes = listOf(),
            state = ServiceState.InitializationFailed("interfaces", "exception"),
            onRemoteClick = {},
            onFavoriteToggle = {},
            onRescan = {},
        )
    }
}

@Preview
@Composable
private fun RemotePaneNotOnlinePreview() {
    WarpinatorTheme {
        RemoteListPaneContent(
            remotes = listOf(),
            isOnline = false,
            onRemoteClick = {},
            onFavoriteToggle = {},
            onRescan = {},
            state = ServiceState.Ok,
        )
    }
}

@Preview
@Composable
private fun RemotePaneNotOnlineContentPreview() {
    val remote = Remote(
        uuid = "remote",
        displayName = "Test Device",
        userName = "user",
        hostname = "hostname",
        status = Remote.RemoteStatus.Connected,
        isFavorite = false,
    )


    WarpinatorTheme {
        RemoteListPaneContent(
            remotes = listOf(remote),
            isOnline = false,
            onRemoteClick = {},
            onFavoriteToggle = {},
            onRescan = {},
            state = ServiceState.Ok,
        )
    }
}
