package slowscript.warpinator.core.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.MulticastLock
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import slowscript.warpinator.core.data.ServiceState
import slowscript.warpinator.core.data.WarpinatorRepository
import slowscript.warpinator.core.model.Remote
import slowscript.warpinator.core.network.Authenticator
import slowscript.warpinator.core.network.Server
import slowscript.warpinator.core.notification.WarpinatorNotificationManager
import slowscript.warpinator.core.utils.Utils
import java.io.File
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject

@AndroidEntryPoint
class MainService : LifecycleService() {
    @Inject
    lateinit var repository: WarpinatorRepository

    @Inject
    lateinit var authenticator: Authenticator

    @Inject
    lateinit var remotesManager: RemotesManager

    @Inject
    lateinit var server: dagger.Lazy<Server>

    @Inject
    lateinit var notificationManager: WarpinatorNotificationManager

    var runningTransfers: Int = 0
    var notificationMgr: NotificationManagerCompat? = null

    private var timer: Timer? = null
    private var logcatProcess: Process? = null
    private var lock: MulticastLock? = null
    private var connMgr: ConnectivityManager? = null
    private var networkCallback: NetworkCallback? = null
    private var apStateChangeReceiver: BroadcastReceiver? = null
    private var autoStopTask: TimerTask? = null

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return MainServiceBinder(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        return false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        notificationMgr = NotificationManagerCompat.from(this)

        // Start logging
        if (repository.prefs.debugLog) {
            logcatProcess = launchLogcat()
        }

        // Acquire multicast lock for mDNS
        acquireMulticastLock()

        // Server needs to load interface setting before this
        repository.currentIPInfo = Utils.iPAddress(this, server.get().networkInterface)
        Log.d(TAG, Utils.dumpInterfaces() ?: "No interfaces")

        // Sometimes fails. Maybe takes too long to get here?
        startForeground(
            WarpinatorNotificationManager.FOREGROUND_SERVICE_ID,
            notificationManager.createForegroundNotification(),
        )
        Log.v(TAG, "Entered foreground")

        // Actually start server if possible. This takes a long time so should be after startForeground()
        if (repository.currentIPInfo != null) {
            authenticator.serverCertificate // Generate cert on start if doesn't exist

            if (authenticator.certException != null) {
                repository.updateServiceState(
                    ServiceState.InitializationFailed(
                        Utils.dumpInterfaces(), authenticator.certException.toString(),
                    ),
                )
                Log.w(TAG, "Server will not start due to error")
            } else {
                server.get().start()
            }
        }

        startPeriodicTasks()

        listenOnNetworkChanges()

        // Consume active transfers
        lifecycleScope.launch {
            repository.remoteListState.collect { remotes ->
                val isTransferring = notificationManager.updateProgressNotification(remotes)

                // Update local tracking for auto-stop logic
                runningTransfers = if (isTransferring) 1 else 0
            }
        }

        // Notify the tile service that MainService just started
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            TileMainService.requestListeningState(this)
        }

        repository.updateServiceState(ServiceState.Ok)

        return START_STICKY
    }

    private fun startPeriodicTasks() {
        lifecycleScope.launch {
            delay(5)

            while (isActive) {
                try {
                    pingRemotes()
                } catch (e: Exception) {
                    Log.e(TAG, "Ping failed", e)
                }
                delay(pingTime)
            }
        }

        lifecycleScope.launch {
            delay(5)
            while (isActive) {
                try {
                    autoReconnect()
                } catch (e: Exception) {
                    Log.e(TAG, "Auto reconnect failed", e)
                }
                delay(reconnectTime)
            }
        }
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Task removed")
        if (runningTransfers == 0) // && autostop enabled
            autoStop()
        super.onTaskRemoved(rootIntent)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        Log.e(TAG, "Service has run out of time and must be stopped (Android 15+)")
        stopSelf()
    }

    private fun stopServer() {
        repository.updateServiceState(ServiceState.Stopping)
        // Create a copy of values to avoid concurrent modification during iteration
        for (r in repository.remoteListState.value) {
            if (r.status == Remote.RemoteStatus.Connected) {
                remotesManager.getWorker(r.uuid)?.disconnect(r.hostname)
            }
        }

        repository.clearRemotes()

        notificationManager.cancelAll()

        lifecycleScope.launch { server.get().stop() }
        notificationMgr?.cancelAll()

        if (connMgr != null && networkCallback != null) {
            try {
                connMgr?.unregisterNetworkCallback(networkCallback!!)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering callback", e)
            }
        }

        if (apStateChangeReceiver != null) {
            try {
                unregisterReceiver(apStateChangeReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
        }

        timer?.cancel()

        try {
            lock?.release()
        } catch (_: Exception) {
        }

        logcatProcess?.destroy()
    }

    private fun autoStop() {
        if (!isAutoStopEnabled) return
        Log.i(TAG, "Autostopping")
        stopSelf()
        autoStopTask = null
    }

    private fun launchLogcat(): Process? {
        val output = File(getExternalFilesDir(null), "latest.log")
        var process: Process? = null
        val cmd = "logcat -f ${output.absolutePath}\n"
        try {
            output.delete() // Delete original file
            process = Runtime.getRuntime().exec(cmd)
            Log.d(TAG, "---- Logcat started ----")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start logging to file", e)
        }
        return process
    }

    private fun listenOnNetworkChanges() {
        connMgr = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        val nr = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED).build()

        networkCallback = object : NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "New network")
                repository.updateNetworkState { it.copy(isConnected = true) }
                onNetworkChanged()
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                repository.updateNetworkState { it.copy(isConnected = true) }
                onNetworkLost()
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                Log.d(TAG, "Link properties changed")
                onNetworkChanged()
            }
        }

        apStateChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val apState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0)
                if (apState % 10 == WifiManager.WIFI_STATE_ENABLED) {
                    Log.d(TAG, "AP was enabled")
                    repository.updateNetworkState { it.copy(isHotspot = true) }
                    onNetworkChanged()
                } else if (apState % 10 == WifiManager.WIFI_STATE_DISABLED) {
                    Log.d(TAG, "AP was disabled")
                    repository.updateNetworkState { it.copy(isHotspot = true) }
                    onNetworkLost()
                }
            }
        }

        // Manually get state, some devices don't fire broadcast when registered
        repository.updateNetworkState { it.copy(isHotspot = isHotspotOn) }
        registerReceiver(
            apStateChangeReceiver, IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED"),
        )

        networkCallback?.let {
            connMgr?.registerNetworkCallback(nr, it)
        }
    }

    private val isHotspotOn: Boolean
        get() {
            val manager =
                applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager ?: return false
            try {
                val method = manager.javaClass.getDeclaredMethod("isWifiApEnabled")
                method.isAccessible = true
                return method.invoke(manager) as? Boolean ?: false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get hotspot state", e)
            }
            return false
        }

    fun gotNetwork() = repository.networkState.value.isOnline

    private fun onNetworkLost() {
        if (!gotNetwork()) repository.currentIPInfo =
            null // Rebind even if we reconnected to the same net
    }

    private fun onNetworkChanged() {
        val newInfo = Utils.iPAddress(this, server.get().networkInterface)
        if (newInfo == null) {
            Log.w(TAG, "Network changed, but we do not have an IP")
            repository.currentIPInfo = null
            return
        }

        val newIP = newInfo.address
        val oldIP = repository.currentIPInfo?.address

        if (newIP != oldIP) {
            Log.d(TAG, ":: Restarting. New IP: $newIP")
            repository.updateServiceState(ServiceState.NetworkChangeRestart)
            repository.currentIPInfo = newInfo

            // Regenerate cert
            authenticator.serverCertificate

            // Restart server
            lifecycleScope.launch {
                server.get().stop()

                if (authenticator.certException == null) {
                    server.get().start()
                } else {
                    Log.w(TAG, "No cert. Server not started.")
                }
            }
        }
    }

    private suspend fun pingRemotes() = withContext(Dispatchers.IO) {
        try {
            for (r in repository.remoteListState.value) {
                if ((r.api == 1) && (r.status == Remote.RemoteStatus.Connected)) {
                    remotesManager.getWorker(r.uuid)?.ping()
                }
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun autoReconnect() = withContext(Dispatchers.IO) {
        if (!gotNetwork()) return@withContext
        try {
            for (r in repository.remoteListState.value) {
                if ((r.status == Remote.RemoteStatus.Disconnected || r.status is Remote.RemoteStatus.Error) && r.serviceAvailable && !r.hasErrorGroupCode) {
                    // Try reconnecting
                    Log.d(TAG, "Automatically reconnecting to ${r.hostname}")
                    remotesManager.getWorker(r.uuid)
                        ?.connect(r.hostname, r.address, r.authPort, r.port, r.api)
                }
            }
        } catch (_: Exception) {
        }
    }


    private fun acquireMulticastLock() {
        val wifi = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
        if (wifi != null) {
            lock = wifi.createMulticastLock("WarpMDNSLock")
            lock?.setReferenceCounted(true)
            lock?.acquire()
            Log.d(TAG, "Multicast lock acquired")
        }
    }

    private val isAutoStopEnabled: Boolean
        get() = repository.prefs.autoStop && !repository.prefs.bootStart

    fun notifyDeviceCountUpdate() {
        // TODO(raresvanca): remove this function and subscribe to the repos client manager for updates
    }

    companion object {
        private const val TAG = "SERVICE"

        const val ACTION_STOP: String = "StopSvc"

        var pingTime: Long = 10000
        var reconnectTime: Long = 40000

        fun dumpLog(): File? {
//            Log.d(TAG, "Saving log...")
//            val service
//            val output = File(service.externalCacheDir, "dump.log")
//            val cmd = "logcat -d -f ${output.absolutePath}\n"
//            try {
//                val process = Runtime.getRuntime().exec(cmd)
//                process.waitFor()
//            } catch (e: Exception) {
//                Log.e(TAG, "Failed to dump log", e)
//                return null
//            }
//            return output
            // TODO(raresvanca): move this to the repo
            return null
        }
    }
}

