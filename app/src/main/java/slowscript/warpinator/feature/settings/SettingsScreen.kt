package slowscript.warpinator.feature.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import slowscript.warpinator.R
import slowscript.warpinator.app.LocalNavController
import slowscript.warpinator.core.design.components.DynamicAvatarCircle
import slowscript.warpinator.core.design.shapes.segmentedDynamicShapes
import slowscript.warpinator.core.design.theme.WarpinatorTheme
import slowscript.warpinator.core.network.Server
import slowscript.warpinator.core.utils.ProfilePicturePainter
import slowscript.warpinator.feature.settings.components.OptionsDialog
import slowscript.warpinator.feature.settings.components.ProfilePictureDialog
import slowscript.warpinator.feature.settings.components.SettingsCategoryLabel
import slowscript.warpinator.feature.settings.components.SwitchListItem
import slowscript.warpinator.feature.settings.components.TextInputDialog
import slowscript.warpinator.feature.settings.state.SettingsEvent
import slowscript.warpinator.feature.settings.state.SettingsUiState
import slowscript.warpinator.feature.settings.state.SettingsViewModel
import slowscript.warpinator.feature.settings.state.ThemeOptions

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(), launchDirPicker: Boolean = false
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val navController = LocalNavController.current

    // Observe ViewModel Events (Toast)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowToast -> {
                    Toast.makeText(
                        context,
                        event.messageId,
                        if (event.isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                    ).show()
                }

                is SettingsEvent.ShowToastString -> {
                    Toast.makeText(
                        context,
                        event.message,
                        if (event.isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val dirPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                viewModel.setDirectory(uri)
            }
        }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            // Take permission to read the file
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Ignore if specific permission grant fails (file might still be readable once)
            }
            // Pass to ViewModel for processing
            viewModel.handleCustomProfilePicture(uri)
        }
    }

    // Auto-launch picker if requested via Intent
    LaunchedEffect(Unit) {
        if (launchDirPicker) dirPickerLauncher.launch(null)
    }

    SettingsScreenContent(
        state = state,
        onBackClick = { navController?.popBackStack() },
        onDisplayNameChange = viewModel::setDisplayName,
        onProfilePictureChange = viewModel::setProfilePicture,
        onPickCustomProfilePicture = {
            imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onPickDownloadDir = { dirPickerLauncher.launch(null) },
        onResetDownloadDir = viewModel::resetDirectory,
        onNotifyIncomingChange = viewModel::setNotifyIncoming,
        onAllowOverwriteChange = viewModel::setAllowOverwrite,
        onAutoAcceptChange = viewModel::setAutoAccept,
        onUseCompressionChange = viewModel::setUseCompression,
        onStartOnBootChange = viewModel::setStartOnBoot,
        onAutoStopChange = viewModel::setAutoStop,
        onDebugLogChange = viewModel::setDebugLog,
        onGroupCodeChange = viewModel::setGroupCode,
        onServerPortChange = viewModel::setServerPort,
        onAuthPortChange = viewModel::setAuthPort,
        onNetworkInterfaceChange = viewModel::setNetworkInterface,
        onThemeChange = viewModel::updateTheme
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreenContent(
    state: SettingsUiState,
    onBackClick: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onProfilePictureChange: (String) -> Unit,
    onPickCustomProfilePicture: () -> Unit,
    onPickDownloadDir: () -> Unit,
    onResetDownloadDir: () -> Unit,
    onNotifyIncomingChange: (Boolean) -> Unit,
    onAllowOverwriteChange: (Boolean) -> Unit,
    onAutoAcceptChange: (Boolean) -> Unit,
    onUseCompressionChange: (Boolean) -> Unit,
    onStartOnBootChange: (Boolean) -> Unit,
    onAutoStopChange: (Boolean) -> Unit,
    onDebugLogChange: (Boolean) -> Unit,
    onGroupCodeChange: (String) -> Unit,
    onServerPortChange: (String) -> Unit,
    onAuthPortChange: (String) -> Unit,
    onNetworkInterfaceChange: (String) -> Unit,
    onThemeChange: (ThemeOptions) -> Unit
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var showProfileDialog by remember { mutableStateOf(false) }
    var editDialogTitle by remember { mutableStateOf<Int?>(null) }
    var editDialogValue by remember { mutableStateOf("") }
    var editDialogIsNumber by remember { mutableStateOf(false) }
    var onEditConfirm by remember { mutableStateOf<(String) -> Unit>({}) }
    var showEditDialog by remember { mutableStateOf(false) }

    var showThemeDialog by remember { mutableStateOf(false) }
    var showInterfaceDialog by remember { mutableStateOf(false) }

    fun openEdit(
        titleRes: Int, currentValue: String, isNumber: Boolean = false, onConfirm: (String) -> Unit
    ) {
        editDialogTitle = titleRes
        editDialogValue = currentValue
        editDialogIsNumber = isNumber
        onEditConfirm = onConfirm
        showEditDialog = true
    }

    val listItemColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer
    )
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings)) },

                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,

                )
        }) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
            contentPadding = innerPadding,
        ) {
            item {
                SettingsCategoryLabel(stringResource(R.string.identity_settings_category))

                SegmentedListItem(
                    content = { Text(stringResource(R.string.display_settings_title)) },
                    supportingContent = { Text(state.displayName) },
                    onClick = {
                        openEdit(
                            R.string.display_settings_title, state.displayName
                        ) { onDisplayNameChange(it) }
                    },
                    shapes = ListItemDefaults.segmentedDynamicShapes(0, 3),
                    colors = listItemColors,
                    modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap)
                )
                val profilePictureBitmap = remember(
                    state.profilePictureKey, state.profileImageSignature
                ) { ProfilePicturePainter.getProfilePicture(state.profilePictureKey, context) }
                SegmentedListItem(
                    content = { Text(stringResource(R.string.picture_settings_title)) },
                    trailingContent = {
                        DynamicAvatarCircle(
                            size = 32.dp,
                            bitmap = profilePictureBitmap,
                        )
                    },
                    onClick = { showProfileDialog = true },
                    shapes = ListItemDefaults.segmentedDynamicShapes(2, 3),
                    colors = listItemColors
                )
            }

            item {
                SettingsCategoryLabel(stringResource(R.string.transfer_setting_category))

                SegmentedListItem(
                    content = { Text(stringResource(R.string.download_dir_settings_title)) },
                    supportingContent = { Text(state.downloadDirSummary) },
                    onClick = { onPickDownloadDir() },
                    trailingContent = {
                        AnimatedVisibility(
                            visible = state.canResetDir, enter = fadeIn(), exit = fadeOut()
                        ) {
                            IconButton(onClick = onResetDownloadDir) {
                                Icon(Icons.Default.Restore, contentDescription = "Reset to default")
                            }
                        }
                    },
                    shapes = ListItemDefaults.segmentedDynamicShapes(0, 3),
                    colors = listItemColors,
                    modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap)
                )

                SwitchListItem(
                    title = stringResource(R.string.notification_settings_title),
                    summary = stringResource(R.string.notification_settings_summary),
                    checked = state.notifyIncoming,
                    onCheckedChange = onNotifyIncomingChange,
                    shapes = ListItemDefaults.segmentedDynamicShapes(1, 3),
                    colors = listItemColors,
                )

                SwitchListItem(
                    title = stringResource(R.string.overwrite_settings_title),
                    summary = if (state.allowOverwrite) stringResource(R.string.overwrite_settings_summary_on)
                    else stringResource(R.string.overwrite_settings_summary_off),
                    checked = state.allowOverwrite,
                    onCheckedChange = onAllowOverwriteChange,
                    shapes = ListItemDefaults.segmentedDynamicShapes(1, 3),
                    colors = listItemColors,
                )

                SwitchListItem(
                    title = stringResource(R.string.accept_settings_title),
                    summary = if (state.autoAccept) stringResource(R.string.accept_setting_summary_on)
                    else stringResource(R.string.accept_setting_summary_off),
                    checked = state.autoAccept,
                    onCheckedChange = onAutoAcceptChange,
                    shapes = ListItemDefaults.segmentedDynamicShapes(1, 3),
                    colors = listItemColors,
                )

                SwitchListItem(
                    title = stringResource(R.string.compression_settings_title),
                    checked = state.useCompression,
                    onCheckedChange = onUseCompressionChange,
                    shapes = ListItemDefaults.segmentedDynamicShapes(2, 3),
                    colors = listItemColors,
                )
            }

            item {
                SettingsCategoryLabel(stringResource(R.string.app_behavior_setting_category))

                // Boot Start (Only for Android < 15/Vanilla Ice Cream)
                val isBootStartSupported =
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM

                SwitchListItem(
                    title = stringResource(R.string.boot_settings_title),
                    summary = if (isBootStartSupported) null else stringResource(R.string.boot_settings_summary_a15),
                    checked = state.startOnBoot,
                    enabled = isBootStartSupported,
                    onCheckedChange = onStartOnBootChange,
                    shapes = ListItemDefaults.segmentedDynamicShapes(0, 3),
                    colors = listItemColors,
                )

                SwitchListItem(
                    title = stringResource(R.string.stop_svc_when_leaving),
                    summary = if (state.autoStop) stringResource(R.string.stop_svc_when_leaving_summary_on)
                    else stringResource(R.string.stop_svc_when_leaving_summary_off),
                    checked = state.autoStop,
                    enabled = state.isAutoStopEnabled, // DISABLED if BootStart is ON
                    onCheckedChange = onAutoStopChange,
                    shapes = ListItemDefaults.segmentedDynamicShapes(1, 3),
                    colors = listItemColors,
                )

                SwitchListItem(
                    title = stringResource(R.string.export_log_settings_tile),
                    summary = "Android/data/slowscript.warpinator/files/", // Hardcoded per XML
                    checked = state.debugLog,
                    onCheckedChange = onDebugLogChange,
                    shapes = ListItemDefaults.segmentedDynamicShapes(2, 3),
                    colors = listItemColors,
                )

            }

            item {
                SettingsCategoryLabel(stringResource(R.string.network_setting_category))

                SegmentedListItem(
                    content = { Text(stringResource(R.string.group_code_settings_title)) },
                    supportingContent = { Text(state.groupCode) },
                    onClick = {
                        openEdit(
                            R.string.group_code_settings_title, state.groupCode
                        ) { onGroupCodeChange(it) }
                    },
                    shapes = ListItemDefaults.segmentedDynamicShapes(0, 3),
                    colors = listItemColors,
                    modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap)
                )

                SegmentedListItem(
                    content = { Text(stringResource(R.string.port_settings_title)) },
                    supportingContent = { Text(state.port) },
                    onClick = {
                        openEdit(
                            R.string.port_settings_title, state.port, isNumber = true
                        ) { onServerPortChange(it) }
                    },
                    shapes = ListItemDefaults.segmentedDynamicShapes(1, 3),
                    colors = listItemColors,
                    modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap)
                )

                SegmentedListItem(
                    content = { Text(stringResource(R.string.auth_port_settings_title)) },
                    supportingContent = { Text(state.authPort) },
                    onClick = {
                        openEdit(
                            R.string.auth_port_settings_title, state.authPort, isNumber = true
                        ) { onAuthPortChange(it) }
                    },
                    shapes = ListItemDefaults.segmentedDynamicShapes(1, 3),
                    colors = listItemColors,
                    modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap)
                )

                SegmentedListItem(
                    content = { Text(stringResource(R.string.network_interface_settings_title)) },
                    supportingContent = {
                        Text(
                            if (state.networkInterface != Server.NETWORK_INTERFACE_AUTO) stringResource(
                                R.string.network_interface_settings_summary, state.networkInterface
                            )
                            else state.networkInterface
                        )
                    },
                    onClick = { showInterfaceDialog = true },
                    shapes = ListItemDefaults.segmentedDynamicShapes(2, 3),
                    colors = listItemColors,

                    )

            }

            item {
                SettingsCategoryLabel(stringResource(R.string.aspect_setting_category))

                val themeLabelResId = state.themeMode.label

                SegmentedListItem(
                    content = { Text(stringResource(R.string.theme_settings_title)) },
                    supportingContent = { Text(stringResource(themeLabelResId)) },
                    onClick = { showThemeDialog = true },
                    shapes = ListItemDefaults.segmentedDynamicShapes(0, 1),
                    colors = listItemColors,
                )

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showProfileDialog) {
        ProfilePictureDialog(
            currentKey = state.profilePictureKey,
            onDismiss = { showProfileDialog = false },
            onSelectKey = {
                onProfilePictureChange(it)
                showProfileDialog = false
            },
            onSelectCustom = {
                onPickCustomProfilePicture()
            },
            imageSignature = state.profileImageSignature,
        )
    } else if (showEditDialog) {
        TextInputDialog(
            titleRes = editDialogTitle!!,
            initialValue = editDialogValue,
            isNumber = editDialogIsNumber,
            onDismiss = { showEditDialog = false },
            onConfirm = { newVal ->
                onEditConfirm(newVal)
                showEditDialog = false
            })
    } else if (showThemeDialog) {
        val values = ThemeOptions.entries

        OptionsDialog(
            title = stringResource(R.string.theme_settings_title),
            options = values.map { e -> stringResource(e.label) }.toList(),
            currentSelectionIndex = values.indexOf(state.themeMode),
            onDismiss = { showThemeDialog = false },
            onOptionSelected = { idx -> onThemeChange(values[idx]) })
    } else if (showInterfaceDialog) {
        val options = state.interfaceEntries.map { it.first }
        val values = state.interfaceEntries.map { it.second }

        OptionsDialog(
            title = stringResource(R.string.network_interface_settings_title),
            options = options,
            currentSelectionIndex = values.indexOf(state.networkInterface),
            onDismiss = { showInterfaceDialog = false },
            onOptionSelected = { idx -> onNetworkInterfaceChange(values[idx]) })
    }
}

@PreviewLightDark
@Composable
fun SettingsScreenPreview() {
    WarpinatorTheme {
        SettingsScreenContent(
            state = SettingsUiState(),
            onBackClick = {},
            onDisplayNameChange = {},
            onProfilePictureChange = {},
            onPickCustomProfilePicture = {},
            onPickDownloadDir = {},
            onResetDownloadDir = {},
            onNotifyIncomingChange = {},
            onAllowOverwriteChange = {},
            onAutoAcceptChange = {},
            onUseCompressionChange = {},
            onStartOnBootChange = {},
            onAutoStopChange = {},
            onDebugLogChange = {},
            onGroupCodeChange = {},
            onServerPortChange = {},
            onAuthPortChange = {},
            onNetworkInterfaceChange = {},
            onThemeChange = {})
    }
}