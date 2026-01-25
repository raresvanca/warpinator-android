package slowscript.warpinator.core.model.preferences

import androidx.annotation.StringRes
import slowscript.warpinator.R
import slowscript.warpinator.core.system.PreferenceManager

/**
 * Represents the available application theme modes.
 */
enum class ThemeOptions(val key: String, @param:StringRes val label: Int) {
    SYSTEM_DEFAULT(PreferenceManager.Companion.VAL_THEME_DEFAULT, R.string.sysDefault), LIGHT_THEME(
        PreferenceManager.Companion.VAL_THEME_LIGHT,
        R.string.lightTheme,
    ),
    DARK_THEME(PreferenceManager.Companion.VAL_THEME_DARK, R.string.darkTheme);

    companion object {
        fun fromKey(key: String?): ThemeOptions {
            return entries.find { it.key == key } ?: SYSTEM_DEFAULT
        }
    }
}