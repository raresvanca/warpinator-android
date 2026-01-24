package slowscript.warpinator.core.network.worker

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import io.grpc.ConnectivityState
import io.grpc.ManagedChannel
import io.grpc.StatusRuntimeException
import io.grpc.okhttp.OkHttpChannelBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import slowscript.warpinator.WarpGrpc
import slowscript.warpinator.WarpGrpcKt
import slowscript.warpinator.WarpProto
import slowscript.warpinator.WarpRegistrationGrpc
import slowscript.warpinator.core.data.WarpinatorRepository
import slowscript.warpinator.core.model.Remote
import slowscript.warpinator.core.model.Transfer
import slowscript.warpinator.core.network.Authenticator
import slowscript.warpinator.core.network.CertServer
import slowscript.warpinator.core.network.Server
import slowscript.warpinator.core.utils.Utils
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

class RemoteWorker(
    private val uuid: String,
    private var repository: WarpinatorRepository,
    private var server: Server,
    private var authenticator: Authenticator,
) {

    private var errorReceiveCert: Boolean = false
    private var hasGroupCodeException: Boolean = false
    private var channel: ManagedChannel? = null
    private var coroutineStub: WarpGrpcKt.WarpCoroutineStub? = null
    private var asyncStub: WarpGrpc.WarpStub? = null
    private var blockingStub: WarpGrpc.WarpBlockingStub? = null

    /** Returns false if connection failed. Overload to take in a remote for easier parameter passing */
    suspend fun connect(remote: Remote) =
        connect(remote.hostname, remote.address, remote.authPort, remote.port, remote.api)

    /** Returns false if connection failed */
    suspend fun connect(
        hostname: String?, address: InetAddress?, authPort: Int, port: Int, api: Int,
    ): Boolean = withContext(
        Dispatchers.IO,
    ) {
        Log.i(TAG, "Connecting to $hostname, api $api")

        repository.updateRemoteStatus(uuid, Remote.RemoteStatus.Connecting)

        if (!receiveCertificate(
                api = api, port = port, hostname = hostname, address = address, authPort = authPort,
            )
        ) {
            if (hasGroupCodeException) {
                repository.updateRemoteStatus(
                    uuid, Remote.RemoteStatus.Error(hasGroupCodeException = true),
                )
            } else {
                repository.updateRemoteStatus(
                    uuid, Remote.RemoteStatus.Error(isCertificateUnreceived = true),
                )
            }
            return@withContext false
        }

        Log.d(TAG, "Certificate for $hostname received and saved")

        try {
            val safeAddress = address ?: throw IllegalStateException("Address is null")

            val builder = OkHttpChannelBuilder.forAddress(safeAddress.hostAddress, port)
                .sslSocketFactory(authenticator.createSSLSocketFactory(uuid))
                .flowControlWindow(1280 * 1024)

            if (api >= 2) {
                builder.keepAliveWithoutCalls(true).keepAliveTime(11, TimeUnit.SECONDS)
                    .keepAliveTimeout(5, TimeUnit.SECONDS)
            }

            channel?.takeIf { !it.isShutdown }?.shutdown()

            val newChannel = builder.build()
            channel = newChannel

            if (api >= 2) {
                newChannel.notifyWhenStateChanged(newChannel.getState(true)) {
                    this@RemoteWorker.onChannelStateChanged(hostname)
                }
            }

            coroutineStub = WarpGrpcKt.WarpCoroutineStub(newChannel)
            blockingStub = WarpGrpc.newBlockingStub(newChannel)
            asyncStub = WarpGrpc.newStub(newChannel)

            withTimeoutOrNull(5000) {
                coroutineStub?.ping(
                    WarpProto.LookupName.newBuilder().setId(server.uuid).build(),
                )
            } ?: throw Exception("Ping timeout")
        } catch (c: CancellationException) {
            throw c
        } catch (e: SSLException) {
            Log.e(TAG, "Authentication with remote $hostname failed: ${e.message}", e)
            repository.updateRemoteStatus(
                uuid, Remote.RemoteStatus.Error(e.localizedMessage ?: "", hasSslException = true),
            )
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to remote $hostname. ${e.message}", e)
            repository.updateRemoteStatus(uuid, Remote.RemoteStatus.Error(e.toString()))
            return@withContext false
        } finally {
            // Clean up channel on failure
            if (channel != null && (repository.getRemoteStatus(uuid) is Remote.RemoteStatus.Error)) {
                channel?.shutdownNow()
            }
        }

        repository.updateRemoteStatus(uuid, Remote.RemoteStatus.AwaitingDuplex)

        if (!waitForDuplex(api = api)) {
            Log.e(TAG, "Couldn't establish duplex with $hostname")
            repository.updateRemoteStatus(
                uuid, Remote.RemoteStatus.Error(isDuplexFailed = true),
            )
            channel?.shutdown()
            return@withContext false
        }

        repository.updateRemoteStatus(uuid, Remote.RemoteStatus.Connected)

        try {
            val info =
                coroutineStub?.getRemoteMachineInfo(WarpProto.LookupName.getDefaultInstance())
            if (info != null) {
                repository.updateRemote(uuid) { remote ->
                    remote.copy(
                        displayName = info.displayName, userName = info.userName,
                    )
                }
            }
        } catch (ex: StatusRuntimeException) {
            repository.updateRemoteStatus(
                uuid,
                Remote.RemoteStatus.Error(
                    ex.localizedMessage ?: "", hasUsernameException = true,
                ),
            )
            Log.e(TAG, "connect: cannot get name: connection broken?", ex)
            channel?.shutdown()
            return@withContext false
        }

        // Get avatar
        try {
            val flow =
                coroutineStub?.getRemoteMachineAvatar(WarpProto.LookupName.getDefaultInstance())
            val bs = com.google.protobuf.ByteString.newOutput()

            flow?.collect { chunk ->
                chunk.avatarChunk.writeTo(bs)
            }

            val bytes = bs.toByteString().toByteArray()
            if (bytes.isNotEmpty()) {
                repository.updateRemotePicture(
                    uuid, BitmapFactory.decodeByteArray(bytes, 0, bytes.size),
                )
            }
        } catch (_: Exception) {
            repository.updateRemotePicture(uuid, null)
        }

        Log.i(TAG, "Connection established with $hostname")

        return@withContext true
    }

    fun disconnect(hostname: String?) {
        Log.i(TAG, "Disconnecting $hostname")
        try {
            channel?.shutdownNow()
        } catch (_: Exception) {
        }
        repository.updateRemoteStatus(uuid, Remote.RemoteStatus.Disconnected)
    }

    private fun onChannelStateChanged(hostname: String?) {
        val currentChannel = channel ?: return
        val state = currentChannel.getState(false)
        Log.d(TAG, "onChannelStateChanged: $hostname -> $state")
        if (state == ConnectivityState.TRANSIENT_FAILURE || state == ConnectivityState.IDLE) {
            repository.updateRemoteStatus(uuid, Remote.RemoteStatus.Disconnected)
            currentChannel.shutdown() //Dispose of channel so it can be recreated if device comes back
        }
        currentChannel.notifyWhenStateChanged(state) { this.onChannelStateChanged(hostname) }
    }

    suspend fun ping() {
        try {
            coroutineStub?.ping(
                WarpProto.LookupName.newBuilder().setId(server.uuid).build(),
            )
        } catch (e: Exception) {
            repository.updateRemoteStatus(uuid, Remote.RemoteStatus.Disconnected)
            channel?.shutdown()
        }
    }


    fun sameSubnetWarning(address: InetAddress?, status: Remote.RemoteStatus): Boolean {
        if (status == Remote.RemoteStatus.Connected) return false
        val ipInfo = repository.currentIPInfo ?: return false
        val currentAddress = address ?: return false

        return !Utils.isSameSubnet(currentAddress, ipInfo.address, ipInfo.prefixLength)
    }


    fun startSendTransfer(t: Transfer) {
        repository.addTransfer(uuid, t)

        val topDirBaseNames = t.topDirBaseNames

        val info = WarpProto.OpInfo.newBuilder().setIdent(server.uuid).setTimestamp(t.startTime)
            .setReadableName(Utils.getDeviceName(repository.appContext))
            .setUseCompression(t.useCompression).build()

        val op = WarpProto.TransferOpRequest.newBuilder().setInfo(info).setSenderName("Android")
            .setReceiver(uuid).setSize(t.totalSize).setCount(t.fileCount)
            .setNameIfSingle(t.singleFileName).setMimeIfSingle(t.singleMimeType)
            .addAllTopDirBasenames(topDirBaseNames).build()

        repository.applicationScope.launch {
            try {
                coroutineStub?.processTransferOpRequest(op)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send transfer request", e)
            }
        }
    }

    suspend fun connectForReceive(tData: Transfer): Flow<WarpProto.FileChunk> {
        val info = WarpProto.OpInfo.newBuilder().setIdent(server.uuid).setTimestamp(tData.startTime)
            .setReadableName(Utils.getDeviceName(repository.appContext))
            .setUseCompression(tData.useCompression).build()

        // This might throw, caller (TransferWorker) must handle exceptions
        return coroutineStub!!.startTransfer(info)
    }

    fun declineTransfer(t: Transfer) {
        val info = WarpProto.OpInfo.newBuilder().setIdent(server.uuid).setTimestamp(t.startTime)
            .setReadableName(Utils.getDeviceName(repository.appContext)).build()
        asyncStub?.cancelTransferOpRequest(info, Utils.VoidObserver())
    }

    fun stopTransfer(t: Transfer, error: Boolean) {
        val i = WarpProto.OpInfo.newBuilder().setIdent(server.uuid).setTimestamp(t.startTime)
            .setReadableName(Utils.getDeviceName(repository.appContext)).build()
        val info = WarpProto.StopInfo.newBuilder().setError(error).setInfo(i).build()
        asyncStub?.stopTransfer(info, Utils.VoidObserver())
    }

    // Private helpers
    private fun receiveCertificate(
        hostname: String?, address: InetAddress?, authPort: Int, port: Int, api: Int,
    ): Boolean {
        hasGroupCodeException = false
        if (api == 2) {
            if (receiveCertificateV2(
                    hostname = hostname, address = address, authPort = authPort,
                )
            ) return true
            else if (hasGroupCodeException) return false
            else Log.d(TAG, "Falling back to receiveCertificateV1")
        }
        return receiveCertificateV1(
            port = port, hostname = hostname, address = address,
        )
    }

    private fun receiveCertificateV1(hostname: String?, address: InetAddress?, port: Int): Boolean {
        var received: ByteArray? = null
        var tryCount = 0

        loop@ while (tryCount < 3) {
            try {
                DatagramSocket().use { sock ->
                    Log.v(TAG, "Receiving certificate from $address, try $tryCount")
                    sock.soTimeout = 1500

                    val req = CertServer.REQUEST.toByteArray()
                    val p = DatagramPacket(req, req.size, address, port)
                    sock.send(p)

                    val receiveData = ByteArray(2000)
                    val receivePacket = DatagramPacket(receiveData, receiveData.size)
                    sock.receive(receivePacket)

                    if (receivePacket.address == address && receivePacket.port == port) {
                        received = receivePacket.data.copyOfRange(0, receivePacket.length)
                        break@loop
                    }
                }
            } catch (e: Exception) {
                tryCount++
                Log.d(TAG, "receiveCertificate: attempt $tryCount failed: ${e.message}")
            }
        }

        if (tryCount == 3) {
            Log.e(TAG, "Failed to receive certificate from $hostname")
            errorReceiveCert = true
            return false
        }

        val safeReceived = received ?: return false
        val decoded = Base64.decode(safeReceived, Base64.DEFAULT)
        hasGroupCodeException = !authenticator.saveBoxedCert(decoded, uuid)

        if (hasGroupCodeException) return false
        errorReceiveCert = false
        return true
    }

    private fun receiveCertificateV2(
        hostname: String?, address: InetAddress?, authPort: Int,
    ): Boolean {
        Log.v(TAG, "Receiving certificate (V2) from $hostname at $address")
        var authChannel: ManagedChannel? = null
        try {
            val safeAddress = address ?: return false

            authChannel =
                OkHttpChannelBuilder.forAddress(safeAddress.hostAddress, authPort).usePlaintext()
                    .build()

            val resp = WarpRegistrationGrpc.newBlockingStub(authChannel).withWaitForReady()
                .withDeadlineAfter(8, TimeUnit.SECONDS).requestCertificate(
                    WarpProto.RegRequest.newBuilder()
                        .setHostname(Utils.getDeviceName(repository.appContext))
                        .setIp(repository.currentIPStr ?: "").build(),
                )

            val lockedCert = resp.lockedCertBytes.toByteArray()
            val decoded = Base64.decode(lockedCert, Base64.DEFAULT)

            hasGroupCodeException = !authenticator.saveBoxedCert(decoded, uuid)
            if (hasGroupCodeException) return false

            errorReceiveCert = false
            return true

        } catch (e: Exception) {
            Log.w(TAG, "Could not receive certificate from $hostname", e)
            errorReceiveCert = true
        } finally {
            authChannel?.shutdownNow()
        }
        return false
    }

    private suspend fun waitForDuplex(api: Int): Boolean {
        return if (api == 2) waitForDuplexV2() else waitForDuplexV1()
    }

    private suspend fun waitForDuplexV1(): Boolean {
        Log.d(TAG, "Waiting for duplex - V1")
        var tries = 0
        while (tries < 10) {
            try {
                val haveDuplex = coroutineStub?.checkDuplexConnection(
                    WarpProto.LookupName.newBuilder().setId(server.uuid).setReadableName("Android")
                        .build(),
                )?.response ?: false

                if (haveDuplex) return true

            } catch (e: Exception) {
                Log.d(TAG, "Error while checking duplex (Attempt $tries)", e)
            }

            Log.d(TAG, "Attempt $tries: No duplex")

            delay(3000)
            tries++
        }
        return false
    }

    private suspend fun waitForDuplexV2(): Boolean {
        Log.d(TAG, "Waiting for duplex - V2")
        return try {
            withTimeoutOrNull(10000) {
                coroutineStub?.waitingForDuplex(
                    WarpProto.LookupName.newBuilder().setId(server.uuid)
                        .setReadableName(Utils.getDeviceName(repository.appContext)).build(),
                )
            }?.response ?: false
        } catch (e: Exception) {
            Log.d(TAG, "Error while waiting for duplex", e)
            false
        }
    }

    companion object {
        const val TAG = "Remote"
    }
}