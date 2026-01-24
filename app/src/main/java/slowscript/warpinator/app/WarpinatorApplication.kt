package slowscript.warpinator.app

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.preference.PreferenceManager
import dagger.hilt.android.HiltAndroidApp
import org.conscrypt.Conscrypt
import java.security.Security

@HiltAndroidApp
class WarpinatorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Security.insertProviderAt(Conscrypt.newProvider(), 1)

        // Clear old persisted URI permissions (except profile picture)
        val picture: String =
            PreferenceManager.getDefaultSharedPreferences(this).getString("profile", "0")!!
        for (u in contentResolver.persistedUriPermissions) {
            if (u.uri.toString() == picture) {
                Log.v(TAG, "keeping permission for $u")
                continue
            }
            Log.v(TAG, "releasing uri permission $u")
            contentResolver.releasePersistableUriPermission(
                u.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    companion object {
        const val TAG: String = "APP"
    }
}
