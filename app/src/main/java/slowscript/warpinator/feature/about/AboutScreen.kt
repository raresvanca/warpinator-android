package slowscript.warpinator.feature.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewDynamicColors
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import slowscript.warpinator.BuildConfig
import slowscript.warpinator.R
import slowscript.warpinator.app.LocalNavController
import slowscript.warpinator.core.design.shapes.segmentedDynamicShapes
import slowscript.warpinator.core.design.theme.WarpinatorTheme
import slowscript.warpinator.core.utils.Utils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("About") }, navigationIcon = {
                IconButton(onClick = { navController?.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }, scrollBehavior = scrollBehavior
            )
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = innerPadding.plus(
                PaddingValues(top = 16.dp, start = 16.dp, end = 16.dp)
            ),
        ) {

            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = MaterialShapes.Cookie9Sided.toShape(),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_warpinator),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(16.dp)
                                    .size(48.dp),
                                colorFilter = ColorFilter.tint(
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )

                        }
                        Spacer(Modifier.size(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Warpinator",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                text = stringResource(
                                    id = R.string.version, BuildConfig.VERSION_NAME
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            item {
                SegmentedListItem(
                    onClick = {
                        Utils.openUrl(
                            context, "https://hosted.weblate.org/engage/warpinator-android/"
                        )
                    },
                    shapes = ListItemDefaults.segmentedDynamicShapes(0, 3),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    content = {
                        Text("Help translate Warpinator")
                    },
                    supportingContent = {
                        Text("Translate Warpinator in your language")
                    },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.Translate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                    },
                )
                Spacer(Modifier.height(ListItemDefaults.SegmentedGap))
                SegmentedListItem(
                    onClick = {
                        Utils.openUrl(
                            context,
                            "https://play.google.com/store/apps/details?id=slowscript.warpinator"
                        )
                    },
                    shapes = ListItemDefaults.segmentedDynamicShapes(1, 3),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    content = {
                        Text("Rate on Google Play")
                    },
                    supportingContent = {
                        Text("Enjoying the app? Rate it on Google Play!")
                    },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.RateReview,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
                Spacer(Modifier.height(ListItemDefaults.SegmentedGap))
                SegmentedListItem(
                    onClick = {
                        Utils.openUrl(context, "https://github.com/slowscript/warpinator-android")
                    },
                    shapes = ListItemDefaults.segmentedDynamicShapes(2, 3),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    content = {
                        Text("See the source code")
                    },
                    supportingContent = {
                        Text("Contributions are welcome")
                    },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
                Spacer(Modifier.height(16.dp))
            }

            item {
                SegmentedListItem(
                    onClick = {
                        Utils.openUrl(
                            context, "https://github.com/slowscript/warpinator-android/issues"
                        )
                    },
                    shapes = ListItemDefaults.segmentedDynamicShapes(0, 2),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    content = {
                        Text("Issues")
                    },
                    supportingContent = {
                        Text("Have an issue? Report it here!")
                    },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.BugReport,
                            contentDescription = null,
                        )
                    },
                )
                Spacer(Modifier.height(ListItemDefaults.SegmentedGap))
                SegmentedListItem(
                    onClick = {
                        Utils.openUrl(context, "https://www.gnu.org/licenses/gpl-3.0.html")
                        // TODO: add a license screen to show all OSS licenses of dependencies and the project
                    },
                    shapes = ListItemDefaults.segmentedDynamicShapes(1, 2),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    content = {
                        Text("License")
                    },
                    supportingContent = {
                        Text("GNU General Public License v3.0")
                    },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.Gavel,
                            contentDescription = null,
                        )
                    },
                )
            }

            item {
                Text(
                    text = stringResource(R.string.warranty),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Justify,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 24.dp)
                )
            }
        }
    }
}

@Composable
@PreviewDynamicColors
@PreviewLightDark
fun AboutScreenPreview() {
    WarpinatorTheme {
        AboutScreen()
    }
}