package slowscript.warpinator.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import slowscript.warpinator.feature.home.panes.MessagesPane
import slowscript.warpinator.feature.home.panes.RemoteListPane
import slowscript.warpinator.feature.home.panes.TransfersPane

@OptIn(
    ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun HomeScreen(
    viewModel: WarpinatorViewModel = hiltViewModel(),
) {
    // The Navigator controls the three panes logic automatically
    val navigator = rememberListDetailPaneScaffoldNavigator<RemoteRoute>(
        scaffoldDirective = calculatePaneScaffoldDirective(
            currentWindowAdaptiveInfo(),
        ).copy(
            // This removes the gap between the panes
            horizontalPartitionSpacerSize = 0.dp,
        ),
    )
    val scope = rememberCoroutineScope()

    // If structure changes (List -> Detail), allow back. If content changes in Detail, don't pop.
    val backBehavior = BackNavigationBehavior.PopUntilScaffoldValueChange

    val scaffoldValue = navigator.scaffoldValue
    val isPrimaryExpanded = scaffoldValue.primary == PaneAdaptedValue.Expanded
    val isSecondaryExpanded = scaffoldValue.secondary == PaneAdaptedValue.Expanded
    val isTertiaryExpanded = scaffoldValue.tertiary == PaneAdaptedValue.Expanded

    val secondaryPaneMode = isPrimaryExpanded && isSecondaryExpanded
    val tertiaryPaneMode = isPrimaryExpanded && isTertiaryExpanded

    // Consumed insets
    val listPaneCI = if (isSecondaryExpanded || isTertiaryExpanded) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.End)
    } else WindowInsets(0)

    val detailPaneCI = when {
        secondaryPaneMode && isTertiaryExpanded -> WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
        secondaryPaneMode -> WindowInsets.safeDrawing.only(WindowInsetsSides.Start)
        !secondaryPaneMode && isTertiaryExpanded -> WindowInsets.safeDrawing.only(WindowInsetsSides.End)
        else -> WindowInsets(0)
    }

    val extraPaneCI = if (tertiaryPaneMode) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Start)
    } else WindowInsets(0)

    Surface(color = MaterialTheme.colorScheme.surface) {
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            defaultBackBehavior = backBehavior,
            listPane = {
                AnimatedPane(
                    modifier = Modifier
                        .fillMaxHeight()
                        .consumeWindowInsets(listPaneCI),
                ) {
                    RemoteListPane(
                        onRemoteClick = { remote ->
                            scope.launch {
                                navigator.navigateTo(
                                    ListDetailPaneScaffoldRole.Detail,
                                    RemoteRoute(remote.uuid),
                                )
                            }
                        },
                        paneMode = secondaryPaneMode,
                        onFavoriteToggle = { remote -> viewModel.toggleFavorite(remote.uuid) },
                    )

                }
            },
            detailPane = {

                AnimatedPane(
                    Modifier.consumeWindowInsets(detailPaneCI),
                ) {
                    val selectedUuid = navigator.currentDestination?.contentKey?.uuid

                    val selectedRemote by viewModel.getRemote(selectedUuid)
                        .collectAsStateWithLifecycle(initialValue = null)

                    if (selectedRemote != null) {
                        TransfersPane(
                            remote = selectedRemote!!,
                            paneMode = secondaryPaneMode,
                            onBack = { scope.launch { navigator.navigateBack(backBehavior) } },
                            onFavoriteToggle = { remote -> viewModel.toggleFavorite(selectedRemote!!.uuid) },
                            onOpenMessagesPane = {
                                scope.launch {
                                    navigator.navigateTo(
                                        ListDetailPaneScaffoldRole.Extra,
                                        RemoteRoute(selectedRemote!!.uuid),
                                    )
                                }
                            },
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
            extraPane = {
                AnimatedPane(
                    Modifier.consumeWindowInsets(extraPaneCI),
                ) {
                    val selectedUuid = navigator.currentDestination?.contentKey?.uuid
                    val selectedRemote by viewModel.getRemote(selectedUuid)
                        .collectAsStateWithLifecycle(initialValue = null)

                    if (selectedRemote?.supportsTextMessages == true) {
                        MessagesPane(
                            remote = selectedRemote!!,
                            paneMode = tertiaryPaneMode,
                            onBack = { scope.launch { navigator.navigateBack(backBehavior) } },
                        )
                    } else {
                        Box(Modifier.fillMaxSize()) {}
                    }
                }
            },
        )
    }
}