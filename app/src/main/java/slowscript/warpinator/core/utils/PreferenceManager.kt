package slowscript.warpinator.core.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.StateFlow
import slowscript.warpinator.core.model.preferences.RecentRemote
import slowscript.warpinator.core.model.preferences.SavedFavourite
import slowscript.warpinator.core.network.Server.Companion.NETWORK_INTERFACE_AUTO
import slowscript.warpinator.core.utils.Utils.generateServiceName


class PreferenceManager(
    val context: Context,
    val favourites: StateFlow<Set<SavedFavourite>>,
    val recentRemotes: StateFlow<List<RecentRemote>>
) {

    var loadedPreferences = false
    var prefs: SharedPreferences? = null

    // All app settings
    var debugLog: Boolean = false
    var autoStop = true
    var bootStart = false

    // Server settings
    var serviceUuid: String? = null
    var displayName: String = "Android"

    var port: Int = 42000
    var authPort: Int = 42001
    var networkInterface: String = NETWORK_INTERFACE_AUTO
    var groupCode: String = "Warpinator"

    var allowOverwrite: Boolean = false
    var autoAccept: Boolean = false
    var useCompression: Boolean = false
    var notifyIncoming: Boolean = false
    var downloadDirUri: String? = null
    var profilePicture: String? = "0"


    fun loadSettings() {
        val sharedPreferences =
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) ?: return

        prefs = sharedPreferences


        debugLog = sharedPreferences.getBoolean(KEY_DEBUG_LOG, debugLog)
        autoStop = sharedPreferences.getBoolean(KEY_AUTO_STOP, autoStop)
        bootStart = sharedPreferences.getBoolean(KEY_START_ON_BOOT, bootStart)

        serviceUuid = sharedPreferences.getString(KEY_UUID, serviceUuid)
        displayName =
            sharedPreferences.getString(KEY_DISPLAY_NAME, displayName) ?: DEFAULT_DISPLAY_NAME

        port = sharedPreferences.getString(KEY_PORT, port.toString())?.toInt() ?: port
        authPort =
            sharedPreferences.getString(KEY_AUTH_PORT, authPort.toString())?.toInt() ?: authPort

        networkInterface =
            sharedPreferences.getString(KEY_NETWORK_INTERFACE, networkInterface) ?: networkInterface
        groupCode = sharedPreferences.getString(KEY_GROUP_CODE, groupCode) ?: groupCode

        allowOverwrite = sharedPreferences.getBoolean(KEY_ALLOW_OVERWRITE, allowOverwrite)
        useCompression = sharedPreferences.getBoolean(KEY_USE_COMPRESSION, useCompression)
        notifyIncoming = sharedPreferences.getBoolean(KEY_NOTIFY_INCOMING, notifyIncoming)
        autoAccept = sharedPreferences.getBoolean(KEY_AUTO_ACCEPT, autoAccept)
        downloadDirUri = sharedPreferences.getString(KEY_DOWNLOAD_DIR, downloadDirUri)
        profilePicture = sharedPreferences.getString(KEY_PROFILE_PICTURE, profilePicture)

        if (!sharedPreferences.contains(KEY_PROFILE_PICTURE)) sharedPreferences.edit {
            putString(KEY_PROFILE_PICTURE, DEFAULT_PROFILE_PICTURE)
        }
        profilePicture = sharedPreferences.getString(
            KEY_PROFILE_PICTURE,
            DEFAULT_PROFILE_PICTURE
        )

        if (bootStart && autoStop) sharedPreferences.edit {
            putBoolean(KEY_AUTO_STOP, false)
        }

        // THe pattern for remotes: "$host | $hostname"

        loadedPreferences = true
    }

    fun saveServiceUuid(uuid: String) {
        prefs?.edit { putString(PreferenceManager.KEY_UUID, generateServiceName(context)) }
    }

    fun saveDisplayName(name: String) {
        prefs?.edit { putString(PreferenceManager.KEY_DISPLAY_NAME, name) }
    }

    companion object {
        const val KEY_UUID = "uuid"
        const val KEY_DISPLAY_NAME = "displayName"
        const val KEY_PROFILE_PICTURE = "profile"
        const val KEY_DOWNLOAD_DIR = "downloadDir"
        const val KEY_NOTIFY_INCOMING = "notifyIncoming"
        const val KEY_ALLOW_OVERWRITE = "allowOverwrite"
        const val KEY_AUTO_ACCEPT = "autoAccept"
        const val KEY_USE_COMPRESSION = "useCompression"
        const val KEY_START_ON_BOOT = "bootStart"
        const val KEY_AUTO_STOP = "autoStop"
        const val KEY_DEBUG_LOG = "debugLog"
        const val KEY_GROUP_CODE = "groupCode"
        const val KEY_PORT = "port"
        const val KEY_AUTH_PORT = "authPort"
        const val KEY_NETWORK_INTERFACE = "networkInterface"
        const val KEY_THEME = "theme_setting"
        const val KEY_FAVORITES = "favorites"
        const val KEY_RECENT_REMOTES = "recentRemotes"

        // Defaults
        const val DEFAULT_DISPLAY_NAME = "Android"
        const val DEFAULT_PORT = "42000"
        const val DEFAULT_AUTH_PORT = "42001"
        const val DEFAULT_GROUP_CODE = "Warpinator"
        const val DEFAULT_PROFILE_PICTURE = "0"
        const val DEFAULT_NETWORK_INTERFACE = "auto"

        // Theme Values
        const val VAL_THEME_DEFAULT = "sysDefault"
        const val VAL_THEME_LIGHT = "lightTheme"
        const val VAL_THEME_DARK = "darkTheme"

        // File/Folder Names
        const val DIR_NAME_WARPINATOR = "Warpinator"
        const val FILE_PROFILE_PIC = "profilePic.png"
    }
}