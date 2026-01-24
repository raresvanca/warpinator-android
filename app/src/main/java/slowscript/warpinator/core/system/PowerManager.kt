package slowscript.warpinator.core.system

import android.content.Context
import android.os.PowerManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WarpinatorPowerManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private var wakeLock: PowerManager.WakeLock? = null
    private var lockCount = 0

    init {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        // PARTIAL_WAKE_LOCK ensures the CPU runs, but screen can turn off.
        wakeLock =
            powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG::WakeLock").apply {
                // Important: Reference counting allows multiple transfers to "hold" the lock.
                // The CPU stays awake until the lock is released as many times as it was acquired.
                setReferenceCounted(true)
            }
    }

    fun acquire() {
        try {
            // 10 minute timeout as a safety net per acquisition in case of logic bugs
            wakeLock?.acquire(10 * 60 * 1000L)
            lockCount++
            Log.v(TAG, "WakeLock acquired. Held count: $lockCount")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }
    }

    fun release() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                lockCount--
                Log.v(TAG, "WakeLock released. Held count: $lockCount")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release WakeLock", e)
        }
    }


    companion object {
        private const val TAG = "WarpinatorPowerManager"
    }
}