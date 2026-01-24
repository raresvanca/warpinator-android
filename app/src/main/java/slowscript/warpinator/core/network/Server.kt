package slowscript.warpinator.core.network

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Bitmap
import android.util.Log
import com.google.common.net.InetAddresses
import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.netty.GrpcSslContexts
import io.grpc.netty.NettyServerBuilder
import io.grpc.okhttp.OkHttpChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.conscrypt.Conscrypt
import slowscript.warpinator.WarpProto.ServiceRegistration
import slowscript.warpinator.WarpRegistrationGrpc
import slowscript.warpinator.core.data.ManualConnectionResult
import slowscript.warpinator.core.data.WarpinatorRepository
import slowscript.warpinator.core.model.Remote
import slowscript.warpinator.core.model.Remote.RemoteStatus
import slowscript.warpinator.core.model.preferences.SavedFavourite
import slowscript.warpinator.core.service.GrpcService
import slowscript.warpinator.core.service.RegistrationService
import slowscript.warpinator.core.service.RemotesManager
import slowscript.warpinator.core.service.TransfersManager
import slowscript.warpinator.core.utils.ProfilePicturePainter
import slowscript.warpinator.core.utils.Utils
import slowscript.warpinator.core.utils.Utils.generateServiceName
import slowscript.warpinator.core.utils.Utils.isSameSubnet
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.security.cert.CertificateException
import java.util.Collections
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import javax.jmdns.impl.DNSIncoming
import javax.jmdns.impl.DNSOutgoing
import javax.jmdns.impl.DNSQuestion
import javax.jmdns.impl.DNSRecord
import javax.jmdns.impl.JmDNSImpl
import javax.jmdns.impl.ServiceInfoImpl
import javax.jmdns.impl.constants.DNSConstants
import javax.jmdns.impl.constants.DNSRecordClass
import javax.jmdns.impl.constants.DNSRecordType
import javax.jmdns.impl.tasks.resolver.ServiceResolver

@Singleton
class Server @Inject constructor(
    val remotesManager: RemotesManager,
    val certServer: CertServer,
    val authenticator: Authenticator,
    val repository: WarpinatorRepository,
    val transfersManager: TransfersManager
) {
    var displayName: String? = null
    var port: Int = 0
    var authPort: Int = 0
    var uuid: String? = null
    var profilePicture: String? = null
    var networkInterface: String? = null

    var allowOverwrite: Boolean = false

    var notifyIncoming: Boolean = false


    var downloadDirUri: String? = null
    var running: Boolean = false

    var useCompression: Boolean = false

    var jmdns: JmDNS? = null
    private var serviceInfo: ServiceInfo? = null
    private val serviceListener: ServiceListener
    private val preferenceChangeListener: OnSharedPreferenceChangeListener
    private var gServer: Server? = null
    private var regServer: Server? = null
    private var apiVersion = 2


    init {
        loadSettings()

        serviceListener = newServiceListener()
        preferenceChangeListener =
            OnSharedPreferenceChangeListener { _: SharedPreferences?, _: String? -> loadSettings() }
    }

    fun start() {
        Log.i(TAG, "--- Starting server")
        running = true
        repository.applicationScope.launch(Dispatchers.IO) {
            startGrpcServer()
            startRegistrationServer()
            launch { certServer.start(port) }
            startMDNS()
        }
        repository.prefs.prefs?.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    suspend fun stop() = withContext(Dispatchers.IO + NonCancellable) {
        running = false
        certServer.stop()
        stopMDNS()
        repository.prefs.prefs?.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        if (gServer != null) gServer!!.shutdownNow()
        if (regServer != null) regServer!!.shutdownNow()
        try {
            gServer?.awaitTermination()
        } catch (_: InterruptedException) {
        }
        //LocalBroadcasts.updateNetworkState(svc);
        Log.i(TAG, "--- Server stopped")
    }

    private suspend fun startMDNS() = withContext(Dispatchers.IO) {
        try {
            val address: InetAddress = repository.currentIPInfo!!.address
            Log.d(TAG, "Starting mDNS on $address")
            jmdns = JmDNS.create(address)

            registerService(false)
            delay(500)

            //Start looking for others
            jmdns!!.addServiceListener(SERVICE_TYPE, serviceListener)
        } catch (e: Exception) {
            running = false
            Log.e(TAG, "Failed to init JmDNS", e)
//            LocalBroadcasts.displayToast(svc, "Failed to start JmDNS", 0)
        }
    }

    private suspend fun stopMDNS() = withContext(Dispatchers.IO) {
        if (jmdns != null) {
            try {
                jmdns!!.unregisterAllServices()
                jmdns!!.removeServiceListener(SERVICE_TYPE, serviceListener)
                jmdns!!.close()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to close JmDNS", e)
            }
        }
    }

    fun loadSettings() {
        val prefs = repository.prefs

        if (!prefs.loadedPreferences) return

        if (prefs.serviceUuid == null) prefs.saveServiceUuid(generateServiceName(repository.appContext))
        else uuid = prefs.serviceUuid
        displayName = prefs.displayName
        port = prefs.port
        authPort = prefs.authPort
        networkInterface = prefs.networkInterface
        authenticator.groupCode = prefs.groupCode
        allowOverwrite = prefs.allowOverwrite
        notifyIncoming = prefs.notifyIncoming
        downloadDirUri = prefs.downloadDirUri
        useCompression = prefs.useCompression
        profilePicture = prefs.profilePicture
    }

    fun startGrpcServer() {
        try {
            val cert = File(Utils.certsDir(repository.appContext), ".self.pem")
            val key = File(Utils.certsDir(repository.appContext), ".self.key-pem")
            val ssl =
                GrpcSslContexts.forServer(cert, key).sslContextProvider(Conscrypt.newProvider())
            gServer = NettyServerBuilder.forPort(port).sslContext(ssl.build())
                .addService(GrpcService(repository, remotesManager, transfersManager))
                .permitKeepAliveWithoutCalls(true).permitKeepAliveTime(5, TimeUnit.SECONDS).build()
            gServer!!.start()
            Log.d(TAG, "GRPC server started")
        } catch (e: Exception) {
            running = false
            if (e.cause is CertificateException) {
                Log.e(TAG, "Failed to initialize SSL context", e)
//                Toast.makeText(
//                    svc,
//                    "Failed to start service due to TLS error. Please contact the developers.",
//                    Toast.LENGTH_LONG
//                ).show()
                return
            }
            Log.e(TAG, "Failed to start GRPC server.", e)
//            Toast.makeText(
//                svc,
//                "Failed to start GRPC server. Please try rebooting your phone or changing port numbers.",
//                Toast.LENGTH_LONG
//            ).show()
        }
    }

    fun startRegistrationServer() {
        try {
            regServer =
                NettyServerBuilder.forPort(authPort).addService(RegistrationService()).build()
            regServer!!.start()
            Log.d(TAG, "Registration server started")
        } catch (e: Exception) {
            apiVersion = 1
            Log.w(TAG, "Failed to start V2 registration service.", e)
//            Toast.makeText(
//                svc,
//                "Failed to start V2 registration service. Only V1 will be available.",
//                Toast.LENGTH_LONG
//            ).show()
        }
    }

    suspend fun registerService(flush: Boolean) = withContext(Dispatchers.IO) {
        serviceInfo = ServiceInfo.create(SERVICE_TYPE, uuid, port, "")

        val props: MutableMap<String?, String?> = HashMap()
        props["hostname"] = Utils.getDeviceName(repository.appContext)
        val type = if (flush) "flush" else "real"
        props["type"] = type
        props["api-version"] = apiVersion.toString()
        props["auth-port"] = authPort.toString()
        serviceInfo!!.setText(props)

        // Unregister possibly leftover service info
        // -> Announcement will trigger "new service" behavior and reconnect on other clients
        unregister() //Safe if fails
        delay(250)
        try {
            Log.d(TAG, "Registering as $uuid")
            jmdns!!.registerService(serviceInfo)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to register service.", e)
        }
    }

    val serviceRegistrationMsg: ServiceRegistration
        get() = ServiceRegistration.newBuilder().setServiceId(uuid).setIp(repository.currentIPStr)
            .setPort(port).setHostname(Utils.getDeviceName(repository.appContext))
            .setApiVersion(apiVersion).setAuthPort(authPort).build()

    suspend fun tryRegisterWithHost(host: String): ManualConnectionResult =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Registering with host $host")
            var channel: ManagedChannel? = null
            var flagNotOnSameSubnet = false

            try {
                val sep = host.lastIndexOf(':')
                // Use ip and authPort as specified by user
                val ip = host.take(sep)
                val authPort = host.substring(sep + 1).toInt()
                val ia = InetAddress.getByName(ip)
                flagNotOnSameSubnet = !isSameSubnet(
                    ia, repository.currentIPInfo!!.address, repository.currentIPInfo!!.prefixLength
                )
                channel = OkHttpChannelBuilder.forTarget(host).usePlaintext().build()
                val resp = WarpRegistrationGrpc.newBlockingStub(channel)
                    .registerService(serviceRegistrationMsg)
                Log.d(TAG, "registerWithHost: registration sent")
//                addRecentRemote(host, resp.getHostname())

                var r: Remote? = repository.remoteListState.value.find { it.uuid == resp.serviceId }
                val newRemote = r == null

                if (r == null) {
                    r = Remote(
                        uuid = resp.serviceId,
                        address = null,
                        isFavorite = repository.favouritesState.value.contains(
                            SavedFavourite(resp.serviceId)
                        )
                    )
                } else if (r.status == RemoteStatus.Connected) {
                    return@withContext ManualConnectionResult.AlreadyConnected
                }

                val updatedRemote = r.copy(
                    address = InetAddresses.forString(ip),
                    authPort = authPort,
                    hostname = resp.hostname,
                    api = resp.apiVersion,
                    port = resp.port,
                    serviceName = resp.serviceId,
                    staticService = true
                )

                var connected = false

                if (newRemote) {
                    connected = addRemote(updatedRemote)
                } else {
                    if (updatedRemote.status == RemoteStatus.Disconnected || updatedRemote.status is RemoteStatus.Error) {
                        connected = remotesManager.getWorker(updatedRemote.uuid)?.connect(
                            updatedRemote.hostname,
                            updatedRemote.address,
                            updatedRemote.authPort,
                            updatedRemote.port,
                            updatedRemote.api
                        ) ?: false
                    } else {
                        repository.updateRemote(updatedRemote.uuid) { updatedRemote }
                    }
                }

                if (!connected) {
                    throw Exception("Connection failed")
                }

                // Success!
                return@withContext ManualConnectionResult.Success
            } catch (e: Exception) {
                if (e is StatusRuntimeException && e.status === Status.Code.UNIMPLEMENTED.toStatus()) {
                    Log.e(TAG, "Host $host does not support manual connect -- $e")
                    return@withContext ManualConnectionResult.RemoteDoesNotSupportManualConnect
                } else if (flagNotOnSameSubnet) {
                    Log.e(TAG, "Failed to connect to $host", e)
                    return@withContext ManualConnectionResult.NotOnSameSubnet
                } else {
                    Log.e(TAG, "Failed to connect to $host", e)
                    return@withContext ManualConnectionResult.Error(e.toString())
                }
            } finally {
                channel?.shutdown()
            }
        }

    fun reannounce() {
        repository.applicationScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Reannouncing")
            try {
                var out = DNSOutgoing(DNSConstants.FLAGS_QR_RESPONSE or DNSConstants.FLAGS_AA)
                for (answer in (jmdns as JmDNSImpl).localHost.answers(
                    DNSRecordClass.CLASS_ANY, true, DNSConstants.DNS_TTL
                )) {
                    out = dnsAddAnswer(out, null, answer)
                }
                for (answer in (serviceInfo as ServiceInfoImpl).answers(
                    DNSRecordClass.CLASS_ANY,
                    true,
                    DNSConstants.DNS_TTL,
                    (jmdns as JmDNSImpl).localHost
                )) {
                    out = dnsAddAnswer(out, null, answer)
                }
                (jmdns as JmDNSImpl).send(out)
            } catch (e: Exception) {
                Log.e(TAG, "Reannounce failed", e)
//                LocalBroadcasts.displayToast(
//                    svc,
//                    "Reannounce failed: " + e.message,
//                    Toast.LENGTH_LONG
//                )
            }
        }
    }

    fun rescan() {
        Log.d(TAG, "Rescanning")
        repository.applicationScope.launch(Dispatchers.IO) {
            repository.setRefresh(true)
            //Need a new one every time since it can only run three times
            try {
                val impl = jmdns as? JmDNSImpl
                if (impl == null) {
                    Log.e(TAG, "JmDNS instance is null or invalid")
                    return@launch
                }
                val resolver = ServiceResolver(impl, SERVICE_TYPE)
                val out = DNSOutgoing(DNSConstants.FLAGS_QR_QUERY).apply {
                    val question = DNSQuestion.newQuestion(
                        SERVICE_TYPE, DNSRecordType.TYPE_PTR, DNSRecordClass.CLASS_IN, false
                    )
                    resolver.addQuestion(this, question)
                }
                impl.send(out)
                // A delay to prevent UI flickering
                delay(1000)
            } catch (e: Exception) {
                Log.e(TAG, "Rescan failed", e)
//                LocalBroadcasts.displayToast(svc, "Rescan failed: " + e.message, Toast.LENGTH_LONG)
            } finally {
                repository.setRefresh(false)
            }
        }
    }

    suspend fun unregister() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Unregistering")
        try {
            var out = DNSOutgoing(DNSConstants.FLAGS_QR_RESPONSE or DNSConstants.FLAGS_AA)
            for (answer in (serviceInfo as ServiceInfoImpl).answers(
                DNSRecordClass.CLASS_ANY, true, 0, (jmdns as JmDNSImpl).localHost
            )) {
                out = dnsAddAnswer(out, null, answer)
            }
            (jmdns as JmDNSImpl).send(out)
        } catch (e: Exception) {
            Log.e(TAG, "Unregistering failed", e)
//            LocalBroadcasts.displayToast(
//                svc,
//                "Unregistering failed: " + e.message,
//                Toast.LENGTH_LONG
//            )
        }
    }


    fun dnsAddAnswer(out: DNSOutgoing, `in`: DNSIncoming?, rec: DNSRecord?): DNSOutgoing {
        var newOut = out
        try {
            newOut.addAnswer(`in`, rec)
        } catch (_: IOException) {
            val flags = newOut.flags
            newOut.flags = flags or DNSConstants.FLAGS_TC
            (jmdns as JmDNSImpl).send(newOut)

            newOut = DNSOutgoing(flags, newOut.isMulticast, newOut.maxUDPPayload)
            newOut.addAnswer(`in`, rec)
        }
        return newOut
    }

    suspend fun addRemote(remote: Remote): Boolean {

        // TODO: move the device count to the repository
        val worker = remotesManager.onRemoteDiscovered(remote) ?: return false

        // 2. Attempt connection
        // We don't want to rely on the return value to determine if we "add" it,
        // because onRemoteDiscovered already added it to the repo.
        // We just want to trigger the handshake.
        return worker.connect(
            remote.hostname,
            remote.address,
            remote.authPort,
            remote.port, // Use 'port' here, not 'authPort' twice like in your snippet
            remote.api
        )
    }

    fun newServiceListener(): ServiceListener {
        return object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent) {
                Log.d(TAG, "Service found: " + event.info)
            }

            override fun serviceRemoved(event: ServiceEvent) {
                val svcName = event.info.name
                Log.v(TAG, "Service lost: $svcName")
                repository.updateRemote(svcName) {
                    it.copy(serviceAvailable = false)
                }
            }

            override fun serviceResolved(event: ServiceEvent) {
                val info = event.info
                Log.d(TAG, "*** Service resolved: " + info.name)
                Log.d(TAG, "Details: $info")
                if (info.name == uuid) {
                    Log.v(TAG, "That's me. Ignoring.")
                    return
                }

                //TODO: Same subnet check

                //Ignore flush registration
                val props = Collections.list(info.propertyNames)
                if (props.contains("type") && "flush" == info.getPropertyString("type")) {
                    Log.v(TAG, "Ignoring \"flush\" registration")
                    return
                }
                if (!props.contains("hostname")) {
                    Log.d(
                        TAG,
                        "Ignoring incomplete service info. (no hostname, might be resolved later)"
                    )
                    return
                }

                val svcName = info.name
                val matchedRemote = repository.remoteListState.value.find { it.uuid == svcName }
                if (matchedRemote != null) {
                    Log.d(TAG, "Service already known. Status: " + matchedRemote.status)
                    val newHostname = info.getPropertyString("hostname")
                    val newAuthPort =
                        if (props.contains("auth-port")) info.getPropertyString("auth-port")
                            .toInt() else matchedRemote.authPort

                    val newAddress = getIPv4Address(info.inetAddresses) ?: matchedRemote.address
                    val newPort = info.port

                    val newRemote = matchedRemote.copy(
                        hostname = newHostname,
                        authPort = newAuthPort,
                        address = newAddress,
                        port = newPort,
                        serviceAvailable = true
                    )

                    if ((newRemote.status === RemoteStatus.Disconnected) || (newRemote.status is RemoteStatus.Error)) {
                        Log.d(TAG, "Reconnecting to " + matchedRemote.hostname)
                        repository.addOrUpdateRemote(newRemote)

                        // Launch connection
                        repository.applicationScope.launch(Dispatchers.IO) {
                            remotesManager.onRemoteDiscovered(newRemote)?.connect(newRemote)
                        }
                    } else {
                        repository.addOrUpdateRemote(newRemote)
                    }

                    return
                }

                val newAddress = getIPv4Address(info.inetAddresses) ?: run {
                    Log.w(
                        TAG,
                        "Service resolved with no IPv4 address. Most implementations don't properly support IPv6."
                    )
                    return@serviceResolved
                }
                val newHostname = info.getPropertyString("hostname")
                val newApiVersion =
                    if (props.contains("api-version")) info.getPropertyString("api-version")
                        .toInt() else 1
                val newAuthPort =
                    if (props.contains("auth-port")) info.getPropertyString("auth-port")
                        .toInt() else 0
                val newPort = info.port

                val remote = Remote(
                    uuid = svcName,
                    address = newAddress,
                    hostname = newHostname,
                    authPort = newAuthPort,
                    port = newPort,
                    api = newApiVersion,
                    serviceAvailable = true,
                    serviceName = svcName,
                    isFavorite = repository.favouritesState.value.contains(SavedFavourite(svcName)),
                    status = RemoteStatus.Disconnected
                )

                repository.applicationScope.launch(Dispatchers.IO) { addRemote(remote) }
            }
        }
    }


    val profilePictureBytes: ByteString
        get() {
            val os = ByteArrayOutputStream()
            val bmp = ProfilePicturePainter.getProfilePicture(
                profilePicture!!, repository.appContext, false
            )
            bmp.compress(Bitmap.CompressFormat.PNG, 90, os)
            return ByteString.copyFrom(os.toByteArray())
        }

    private fun getIPv4Address(addresses: Array<InetAddress?>): InetAddress? {
        for (a in addresses) {
            if (a is Inet4Address) return a
        }
        return null
    }

    companion object {
        private const val TAG = "SRV"
        const val SERVICE_TYPE: String = "_warpinator._tcp.local."
        const val NETWORK_INTERFACE_AUTO: String = "auto"
    }
}
