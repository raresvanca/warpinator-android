package slowscript.warpinator.feature.settings.state

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import androidx.annotation.StringRes
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import slowscript.warpinator.R
import slowscript.warpinator.core.model.preferences.ThemeOptions
import slowscript.warpinator.core.system.PreferenceManager
import slowscript.warpinator.core.utils.Utils
import java.io.File
import javax.inject.Inject

sealed interface SettingsEvent {
    data class ShowToast(@param:StringRes val messageId: Int, val isLong: Boolean = false) :
        SettingsEvent

    data class ShowToastString(val message: String, val isLong: Boolean = false) : SettingsEvent
}

/**
 * Represents the immutable UI state for the Settings screen.
 *
 * This state object aggregates all user-configurable preferences, including identity,
 * transfer rules, application behavior, network configuration, and visual themes.
 * It is exposed via a [kotlinx.coroutines.flow.StateFlow] in the [SettingsViewModel].
 *
 */
data class SettingsUiState(
    // Identity
    val displayName: String = PreferenceManager.DEFAULT_DISPLAY_NAME,
    val profilePictureKey: String = PreferenceManager.DEFAULT_PROFILE_PICTURE,
    val profileImageSignature: Long = 0,

    // Transfer
    val downloadDir: String = "",
    val downloadDirSummary: String = "",
    val canResetDir: Boolean = false,
    val notifyIncoming: Boolean = true,
    val allowOverwrite: Boolean = false,
    val autoAccept: Boolean = false,
    val useCompression: Boolean = false,

    // App Behavior / Boot
    val startOnBoot: Boolean = false,
    val autoStop: Boolean = true,
    val isAutoStopEnabled: Boolean = true,
    val debugLog: Boolean = false,

    // Network
    val groupCode: String = PreferenceManager.DEFAULT_GROUP_CODE,
    val port: String = PreferenceManager.DEFAULT_PORT,
    val authPort: String = PreferenceManager.DEFAULT_AUTH_PORT,
    val networkInterface: String = PreferenceManager.DEFAULT_NETWORK_INTERFACE,
    val interfaceEntries: List<Pair<String, String>> = emptyList(),

    // Aspect (Theme)
    val themeMode: ThemeOptions = ThemeOptions.SYSTEM_DEFAULT,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val preferenceManager: PreferenceManager,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            loadSettings()
        }

    init {
        preferenceManager.prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        loadSettings()
        loadInterfaces()
    }

    override fun onCleared() {
        preferenceManager.prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        super.onCleared()
    }

    private fun loadSettings() {
        val savedDownloadPath = preferenceManager.downloadDirUri
        val isCustomDir = savedDownloadPath?.startsWith("content") ?: false

        // Logic for default directory summary
        val downloadPathSummary = if (isCustomDir) {
            savedDownloadPath.toUri().path ?: savedDownloadPath
        } else if (savedDownloadPath == null) {
            "Tap to set"
        } else {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                PreferenceManager.DIR_NAME_WARPINATOR,
            ).absolutePath
        }


        val isStartOnBootEnabled = preferenceManager.bootStart

        _uiState.update { state ->
            state.copy(
                // Identity
                displayName = preferenceManager.displayName,
                profilePictureKey = preferenceManager.profilePicture
                    ?: PreferenceManager.DEFAULT_PROFILE_PICTURE,

                // Transfer
                downloadDir = savedDownloadPath ?: "",
                downloadDirSummary = downloadPathSummary,
                canResetDir = isCustomDir,
                notifyIncoming = preferenceManager.notifyIncoming,
                allowOverwrite = preferenceManager.allowOverwrite,
                autoAccept = preferenceManager.autoAccept,
                useCompression = preferenceManager.useCompression,

                // Boot / App Behavior
                startOnBoot = isStartOnBootEnabled,
                autoStop = preferenceManager.autoStop,
                isAutoStopEnabled = !isStartOnBootEnabled,
                debugLog = preferenceManager.debugLog,

                // Network
                groupCode = preferenceManager.groupCode,
                port = preferenceManager.port.toString(),
                authPort = preferenceManager.authPort.toString(),
                networkInterface = preferenceManager.networkInterface,

                // Theme
                themeMode = ThemeOptions.fromKey(preferenceManager.theme),
            )
        }
    }

    fun setDisplayName(value: String) {
        preferenceManager.setDisplayName(value)
    }

    fun setGroupCode(value: String) {
        preferenceManager.setGroupCode(value)
        viewModelScope.launch { _events.send(SettingsEvent.ShowToast(R.string.requires_restart_warning)) }
    }

    fun setServerPort(value: String) {
        setPortInternal(value) { preferenceManager.setServerPort(it) }
    }

    fun setAuthPort(value: String) {
        setPortInternal(value) { preferenceManager.setAuthPort(it) }
    }

    private fun setPortInternal(value: String, saveAction: (String) -> Unit) {
        val parsedPort = value.toIntOrNull()
        if (parsedPort == null || parsedPort !in 1024..65535) {
            viewModelScope.launch {
                _events.send(SettingsEvent.ShowToast(R.string.port_range_warning, isLong = true))
            }
            return
        }
        saveAction(value)
        viewModelScope.launch { _events.send(SettingsEvent.ShowToast(R.string.requires_restart_warning)) }
    }

    fun setNetworkInterface(value: String) {
        preferenceManager.setNetworkInterface(value)
    }

    fun setNotifyIncoming(value: Boolean) {
        preferenceManager.setNotifyIncoming(value)
    }

    fun setAllowOverwrite(value: Boolean) {
        preferenceManager.setAllowOverwrite(value)
    }

    fun setAutoAccept(value: Boolean) {
        preferenceManager.setAutoAccept(value)
    }

    fun setUseCompression(value: Boolean) {
        preferenceManager.setUseCompression(value)
    }

    fun setStartOnBoot(value: Boolean) {
        preferenceManager.setStartOnBoot(value)
    }

    fun setAutoStop(value: Boolean) {
        preferenceManager.setAutoStop(value)
    }

    fun setDebugLog(value: Boolean) {
        preferenceManager.setDebugLog(value)
        viewModelScope.launch { _events.send(SettingsEvent.ShowToast(R.string.requires_restart_warning)) }
    }

    fun setDirectory(uri: Uri) {
        preferenceManager.setDirectory(uri)
    }

    fun resetDirectory() {
        preferenceManager.resetDirectory()
    }

    fun setProfilePicture(key: String) {
        _uiState.update { it.copy(profileImageSignature = 0) }
        preferenceManager.setProfilePictureKey(key)
    }

    fun updateTheme(value: ThemeOptions) {
        preferenceManager.setTheme(value)
    }

    private fun loadInterfaces() {
        viewModelScope.launch(Dispatchers.IO) {
            val networkInterfaceNames =
                Utils.networkInterfaces ?: arrayOf("Failed to get network interfaces")
            val interfaceDropdownEntries = mutableListOf<Pair<String, String>>()

            interfaceDropdownEntries.add("Auto" to PreferenceManager.DEFAULT_NETWORK_INTERFACE)

            for (interfaceName in networkInterfaceNames) {
                if (interfaceName == null) continue
                var interfaceDisplayLabel = interfaceName
                try {
                    val ip = Utils.getIPForIfaceName(interfaceName)
                    if (ip != null) {
                        interfaceDisplayLabel += " (${ip.address.hostAddress} /${ip.prefixLength})"
                    }
                } catch (e: Exception) { /* Ignored */
                }
                interfaceDropdownEntries.add(interfaceDisplayLabel to interfaceName)
            }

            _uiState.update { it.copy(interfaceEntries = interfaceDropdownEntries) }
        }
    }

    fun handleCustomProfilePicture(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                application.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return@use

                    val maxDimension = 512
                    val (outW, outH) = if (originalBitmap.width > originalBitmap.height) {
                        maxDimension to (originalBitmap.height * maxDimension) / originalBitmap.width
                    } else {
                        (originalBitmap.width * maxDimension) / originalBitmap.height to maxDimension
                    }

                    // Create scaled bitmap
                    val scaledBitmap = originalBitmap.scale(outW, outH)

                    // Save to internal storage
                    application.openFileOutput(
                        PreferenceManager.FILE_PROFILE_PIC,
                        Context.MODE_PRIVATE,
                    ).use { os ->
                        scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                    }

                    _uiState.update {
                        it.copy(
                            // Update timestamp to force reload
                            profileImageSignature = System.currentTimeMillis(),
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                viewModelScope.launch {
                    _events.send(
                        SettingsEvent.ShowToastString(
                            application.getString(R.string.failed_to_save_profile_picture, e),
                            isLong = true,
                        ),
                    )
                }
            }
        }
    }
}