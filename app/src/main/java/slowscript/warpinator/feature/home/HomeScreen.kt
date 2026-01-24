package slowscript.warpinator.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import slowscript.warpinator.core.data.WarpinatorViewModel
import slowscript.warpinator.core.model.ui.RemoteRoute
import slowscript.warpinator.feature.home.panes.RemoteListPane
import slowscript.warpinator.feature.home.panes.TransfersPane

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun HomeScreen(
    viewModel: WarpinatorViewModel = hiltViewModel(),
) {
    // The Navigator controls the "Two Pane" logic automatically
    val navigator = rememberListDetailPaneScaffoldNavigator<RemoteRoute>(
        scaffoldDirective = calculatePaneScaffoldDirective(
            currentWindowAdaptiveInfo(),
        ).copy(
            // This removes the gap between the list and detail panes
            horizontalPartitionSpacerSize = 0.dp,
        ),
    )
    val scope = rememberCoroutineScope()

    // If structure changes (List -> Detail), allow back. If content changes in Detail, don't pop.
    val backBehavior = BackNavigationBehavior.PopUntilScaffoldValueChange
    val paneMode =
        (navigator.scaffoldValue.primary == PaneAdaptedValue.Expanded) && (navigator.scaffoldValue.secondary == PaneAdaptedValue.Expanded)


    Surface(color = MaterialTheme.colorScheme.surface) {
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            defaultBackBehavior = backBehavior,
            listPane = {
                AnimatedPane(modifier = Modifier.fillMaxHeight()) {
                    RemoteListPane(
                        onRemoteClick = { remote ->
                            scope.launch {
                                navigator.navigateTo(
                                    ListDetailPaneScaffoldRole.Detail,
                                    RemoteRoute(remote.uuid),
                                )
                            }
                        },
                        paneMode = paneMode,
                        onFavoriteToggle = { remote -> viewModel.toggleFavorite(remote.uuid) },
                    )

                }
            },
            detailPane = {
                AnimatedPane {
                    val selectedUuid = navigator.currentDestination?.contentKey?.uuid

                    val selectedRemote by viewModel.getRemote(selectedUuid)
                        .collectAsStateWithLifecycle(initialValue = null)

                    if (selectedRemote != null) {
                        TransfersPane(
                            remote = selectedRemote!!,
                            paneMode = paneMode,
                            onBack = { scope.launch { navigator.navigateBack(backBehavior) } },
                            onFavoriteToggle = { remote -> viewModel.toggleFavorite(selectedRemote!!.uuid) },
                        )
                    } else {
                        // Placeholder for Tablet Landscape when no device is selected
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Please select a device",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            },
        )
    }
}