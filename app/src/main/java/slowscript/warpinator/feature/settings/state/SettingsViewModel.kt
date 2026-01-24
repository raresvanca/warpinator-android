package slowscript.warpinator.feature.settings.state

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import slowscript.warpinator.R
import slowscript.warpinator.core.utils.PreferenceManager
import slowscript.warpinator.core.utils.Utils
import java.io.File
import androidx.preference.PreferenceManager as PM

/**
 * Represents the available application theme modes.
 */
enum class ThemeOptions(val key: String, @param:StringRes val label: Int) {
    SYSTEM_DEFAULT(
        PreferenceManager.VAL_THEME_DEFAULT, R.string.sysDefault
    ),
    LIGHT_THEME(
        PreferenceManager.VAL_THEME_LIGHT, R.string.lightTheme
    ),
    DARK_THEME(
        PreferenceManager.VAL_THEME_DARK, R.string.darkTheme
    );

    companion object {
        fun fromKey(key: String?): ThemeOptions {
            return entries.find { it.key == key } ?: SYSTEM_DEFAULT
        }
    }
}

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

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPreferences: SharedPreferences = PM.getDefaultSharedPreferences(application)
    private val context: Context = application.applicationContext

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            loadSettings()
        }

    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        loadSettings()
        loadInterfaces()
    }

    override fun onCleared() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        super.onCleared()
    }

    private fun loadSettings() {
        val savedDownloadPath =
            sharedPreferences.getString(PreferenceManager.KEY_DOWNLOAD_DIR, "") ?: ""
        val isCustomDir = savedDownloadPath.startsWith("content")

        // Logic for default directory summary
        val downloadPathSummary = if (isCustomDir) {
            savedDownloadPath.toUri().path ?: savedDownloadPath
        } else {
            // Fallback to showing the actual default path on disk
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                PreferenceManager.DIR_NAME_WARPINATOR
            ).absolutePath
        }

        val isStartOnBootEnabled =
            sharedPreferences.getBoolean(PreferenceManager.KEY_START_ON_BOOT, false)

        _uiState.update { state ->
            state.copy(
                // Identity
                displayName = sharedPreferences.getString(
                    PreferenceManager.KEY_DISPLAY_NAME, PreferenceManager.DEFAULT_DISPLAY_NAME
                ) ?: PreferenceManager.DEFAULT_DISPLAY_NAME,
                profilePictureKey = sharedPreferences.getString(
                    PreferenceManager.KEY_PROFILE_PICTURE, PreferenceManager.DEFAULT_PROFILE_PICTURE
                ) ?: PreferenceManager.DEFAULT_PROFILE_PICTURE,

                // Transfer
                downloadDir = savedDownloadPath,
                downloadDirSummary = downloadPathSummary,
                canResetDir = isCustomDir,
                notifyIncoming = sharedPreferences.getBoolean(
                    PreferenceManager.KEY_NOTIFY_INCOMING, true
                ),
                allowOverwrite = sharedPreferences.getBoolean(
                    PreferenceManager.KEY_ALLOW_OVERWRITE, false
                ),
                autoAccept = sharedPreferences.getBoolean(PreferenceManager.KEY_AUTO_ACCEPT, false),
                useCompression = sharedPreferences.getBoolean(
                    PreferenceManager.KEY_USE_COMPRESSION, false
                ),

                // Boot / App Behavior
                startOnBoot = isStartOnBootEnabled,
                autoStop = sharedPreferences.getBoolean(PreferenceManager.KEY_AUTO_STOP, true),
                isAutoStopEnabled = !isStartOnBootEnabled, // Logic from java: if bootStart is true, disable autoStop
                debugLog = sharedPreferences.getBoolean(PreferenceManager.KEY_DEBUG_LOG, false),

                // Network
                groupCode = sharedPreferences.getString(
                    PreferenceManager.KEY_GROUP_CODE, PreferenceManager.DEFAULT_GROUP_CODE
                ) ?: PreferenceManager.DEFAULT_GROUP_CODE,
                port = sharedPreferences.getString(
                    PreferenceManager.KEY_PORT, PreferenceManager.DEFAULT_PORT
                ) ?: PreferenceManager.DEFAULT_PORT,
                authPort = sharedPreferences.getString(
                    PreferenceManager.KEY_AUTH_PORT, PreferenceManager.DEFAULT_AUTH_PORT
                ) ?: PreferenceManager.DEFAULT_AUTH_PORT,
                networkInterface = sharedPreferences.getString(
                    PreferenceManager.KEY_NETWORK_INTERFACE,
                    PreferenceManager.DEFAULT_NETWORK_INTERFACE
                ) ?: PreferenceManager.DEFAULT_NETWORK_INTERFACE,

                // Theme
                themeMode = ThemeOptions.fromKey(
                    sharedPreferences.getString(
                        PreferenceManager.KEY_THEME, ""
                    )
                )
            )
        }
    }


    fun setDisplayName(value: String) {
        sharedPreferences.edit { putString(PreferenceManager.KEY_DISPLAY_NAME, value) }
    }

    fun setGroupCode(value: String) {
        sharedPreferences.edit { putString(PreferenceManager.KEY_GROUP_CODE, value) }
        viewModelScope.launch { _events.send(SettingsEvent.ShowToast(R.string.requires_restart_warning)) }
    }

    fun setServerPort(value: String) {
        setPortInternal(PreferenceManager.KEY_PORT, value)
    }

    fun setAuthPort(value: String) {
        setPortInternal(PreferenceManager.KEY_AUTH_PORT, value)
    }

    private fun setPortInternal(key: String, value: String) {
        val parsedPort = value.toIntOrNull()
        if (parsedPort == null || parsedPort !in 1024..65535) {
            viewModelScope.launch {
                _events.send(
                    SettingsEvent.ShowToast(
                        R.string.port_range_warning, isLong = true
                    )
                )
            }
            return
        }
        sharedPreferences.edit { putString(key, value) }
        viewModelScope.launch { _events.send(SettingsEvent.ShowToast(R.string.requires_restart_warning)) }
    }

    fun setNetworkInterface(value: String) {
        sharedPreferences.edit { putString(PreferenceManager.KEY_NETWORK_INTERFACE, value) }
    }

    fun setNotifyIncoming(value: Boolean) {
        sharedPreferences.edit { putBoolean(PreferenceManager.KEY_NOTIFY_INCOMING, value) }
    }

    fun setAllowOverwrite(value: Boolean) {
        sharedPreferences.edit { putBoolean(PreferenceManager.KEY_ALLOW_OVERWRITE, value) }
    }

    fun setAutoAccept(value: Boolean) {
        sharedPreferences.edit { putBoolean(PreferenceManager.KEY_AUTO_ACCEPT, value) }
    }

    fun setUseCompression(value: Boolean) {
        sharedPreferences.edit { putBoolean(PreferenceManager.KEY_USE_COMPRESSION, value) }
    }

    fun setStartOnBoot(value: Boolean) {
        if (value) {
            sharedPreferences.edit {
                putBoolean(
                    PreferenceManager.KEY_START_ON_BOOT, true
                ).putBoolean(PreferenceManager.KEY_AUTO_STOP, false)
            }
        } else {
            sharedPreferences.edit { putBoolean(PreferenceManager.KEY_START_ON_BOOT, false) }
        }
    }

    fun setAutoStop(value: Boolean) {
        sharedPreferences.edit { putBoolean(PreferenceManager.KEY_AUTO_STOP, value) }
    }

    fun setDebugLog(value: Boolean) {
        sharedPreferences.edit { putBoolean(PreferenceManager.KEY_DEBUG_LOG, value) }
        viewModelScope.launch { _events.send(SettingsEvent.ShowToast(R.string.requires_restart_warning)) }
    }

    fun setDirectory(uri: Uri) {
        sharedPreferences.edit { putString(PreferenceManager.KEY_DOWNLOAD_DIR, uri.toString()) }
    }

    fun resetDirectory() {
        sharedPreferences.edit { remove(PreferenceManager.KEY_DOWNLOAD_DIR) }
    }

    fun setProfilePicture(key: String) {
        _uiState.update {
            it.copy(
                // Reset profile image signature to force reload, while not activating the LaunchEffect on reopens of the dialog
                profileImageSignature = 0
            )
        }
        sharedPreferences.edit { putString(PreferenceManager.KEY_PROFILE_PICTURE, key) }
    }

    fun updateTheme(value: ThemeOptions) {
        sharedPreferences.edit { putString(PreferenceManager.KEY_THEME, value.key) }
        when (value) {
            ThemeOptions.SYSTEM_DEFAULT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            ThemeOptions.LIGHT_THEME -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            ThemeOptions.DARK_THEME -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }

    }

    private fun loadInterfaces() {
        val networkInterfaceNames =
            Utils.networkInterfaces ?: arrayOf("Failed to get network interfaces")
        val interfaceDropdownEntries = mutableListOf<Pair<String, String>>()

        interfaceDropdownEntries.add("Auto" to PreferenceManager.DEFAULT_NETWORK_INTERFACE)

        for (interfaceName in networkInterfaceNames) {
            var interfaceDisplayLabel = interfaceName
            try {
                val ip = Utils.getIPForIfaceName(interfaceName)
                if (ip != null) {
                    interfaceDisplayLabel += " (${ip.address.hostAddress} /${ip.prefixLength})"
                }
            } catch (e: Exception) { /* Ignored */
            }
            interfaceDropdownEntries.add(interfaceDisplayLabel!! to interfaceName!!)
        }

        _uiState.update { it.copy(interfaceEntries = interfaceDropdownEntries) }
    }

    fun handleCustomProfilePicture(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
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
                    context.openFileOutput(PreferenceManager.FILE_PROFILE_PIC, Context.MODE_PRIVATE)
                        .use { os ->
                            scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                        }

                    _uiState.update {
                        it.copy(
                            // Update timestamp to force reload
                            profileImageSignature = System.currentTimeMillis()
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                viewModelScope.launch {
                    _events.send(
                        SettingsEvent.ShowToastString(
                            context.getString(R.string.failed_to_save_profile_picture, e),
                            isLong = true
                        )
                    )
                }
            }
        }
    }
}
