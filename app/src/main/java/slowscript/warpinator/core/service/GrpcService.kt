package slowscript.warpinator.core.service

import android.util.Log
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import slowscript.warpinator.WarpGrpcKt
import slowscript.warpinator.WarpProto
import slowscript.warpinator.WarpProto.HaveDuplex
import slowscript.warpinator.WarpProto.LookupName
import slowscript.warpinator.WarpProto.OpInfo
import slowscript.warpinator.WarpProto.RemoteMachineAvatar
import slowscript.warpinator.WarpProto.RemoteMachineInfo
import slowscript.warpinator.WarpProto.StopInfo
import slowscript.warpinator.core.data.WarpinatorRepository
import slowscript.warpinator.core.model.Remote
import slowscript.warpinator.core.model.Transfer
import slowscript.warpinator.core.network.Server
import java.util.UUID

class GrpcService(
    val repository: WarpinatorRepository,
    val server: Server,
    val remotesManager: RemotesManager,
    val transfersManager: TransfersManager,
) : WarpGrpcKt.WarpCoroutineImplBase() {

    val scope = repository.applicationScope

    override suspend fun checkDuplexConnection(
        request: LookupName,
    ): HaveDuplex {
        val id = request.id
        val r: Remote? = repository.remoteListState.value.find { it.uuid == id }
        var haveDuplex = false
        if (r != null) {
            haveDuplex =
                (r.status === Remote.RemoteStatus.Connected) || (r.status === Remote.RemoteStatus.AwaitingDuplex)

            if (r.status is Remote.RemoteStatus.Error || r.status === Remote.RemoteStatus.Disconnected) {
                val serviceInfo = server.jmdns?.getServiceInfo(Server.SERVICE_TYPE, r.uuid)
                if (serviceInfo != null && serviceInfo.inetAddresses.isNotEmpty()) {
                    val newIp = serviceInfo.inetAddresses[0]
                    val newPort = serviceInfo.port
                    repository.updateRemote(r.uuid) { it.copy(address = newIp, port = newPort) }
                    scope.launch {
                        remotesManager.getWorker(uuid = r.uuid)
                            ?.connect(r.hostname, newIp, r.authPort, newPort, r.api)
                    }
                }
            }
        }
        return HaveDuplex.newBuilder().setResponse(haveDuplex).build()
    }

    override suspend fun waitingForDuplex(
        request: LookupName,
    ): HaveDuplex {
        Log.d(TAG, "${request.readableName} is waiting for duplex...")
        val id = request.id
        val r: Remote? = repository.remoteListState.value.find { it.uuid == id }

        if (r != null && (r.status is Remote.RemoteStatus.Error || r.status === Remote.RemoteStatus.Disconnected)) {
            scope.launch { remotesManager.getWorker(uuid = r.uuid)?.connect(r) }
        }

        var i = 0
        var response = false
        while (i < MAX_TRIES) {
            val currentRemote = repository.remoteListState.value.find { it.uuid == id }
            if (currentRemote != null) {
                response =
                    currentRemote.status === Remote.RemoteStatus.AwaitingDuplex || currentRemote.status === Remote.RemoteStatus.Connected
            }
            if (response) break
            i++
            if (i == 32) {
                throw StatusException(Status.DEADLINE_EXCEEDED)
            }
            delay(250)
        }
        return HaveDuplex.newBuilder().setResponse(response).build()
    }

    override suspend fun getRemoteMachineInfo(request: LookupName): RemoteMachineInfo {
        return RemoteMachineInfo.newBuilder().setDisplayName(server.displayName)
            .setUserName("android").build()
    }

    override fun getRemoteMachineAvatar(request: LookupName): Flow<RemoteMachineAvatar> = flow {
        val bytes = server.profilePictureBytes
        emit(RemoteMachineAvatar.newBuilder().setAvatarChunk(bytes).build())
    }

    override suspend fun processTransferOpRequest(request: WarpProto.TransferOpRequest): WarpProto.VoidType {
        val remoteUUID = request.info.ident
        val r: Remote? = repository.remoteListState.value.find { it.uuid == remoteUUID }

        if (r == null) return WarpProto.VoidType.getDefaultInstance()

        Log.i(TAG, "Receiving transfer from " + r.userName)
        if (r.hasErrorGroupCode) return WarpProto.VoidType.getDefaultInstance()

        // Create the Transfer object
        val t = Transfer(
            uid = UUID.randomUUID().toString(),
            remoteUuid = remoteUUID,
            direction = Transfer.Direction.Receive,
            startTime = request.info.timestamp,
            status = Transfer.Status.WaitingPermission,
            totalSize = request.size,
            fileCount = request.count,
            singleMimeType = request.mimeIfSingle,
            singleFileName = request.nameIfSingle,
            topDirBaseNames = request.topDirBasenamesList,
            useCompression = request.info.useCompression && server.useCompression,
        )

        transfersManager.onIncomingTransferRequest(t)

        return WarpProto.VoidType.getDefaultInstance()
    }

    override suspend fun pauseTransferOp(request: OpInfo): WarpProto.VoidType {
        return super.pauseTransferOp(request)
    }

    override fun startTransfer(request: OpInfo): Flow<WarpProto.FileChunk> {
        Log.d(TAG, "Transfer started by the other side")

        val t = getTransfer(request) ?: return flow { }

        // Update compression setting based on agreement
        val updatedTransfer = t.copy(
            useCompression = t.useCompression && request.useCompression,
        )

        repository.updateTransfer(t.remoteUuid, updatedTransfer)

        val worker = transfersManager.getWorker(t.remoteUuid, t.startTime) ?: return flow {
            Log.e(
                TAG, "Worker not found for active transfer",
            )
        }

        return worker.generateFileFlow()
    }

    override suspend fun cancelTransferOpRequest(
        request: OpInfo,
    ): WarpProto.VoidType {
        Log.d(TAG, "Transfer cancelled by the other side")
        val t = getTransfer(request) ?: return WarpProto.VoidType.getDefaultInstance()


        val worker = transfersManager.getWorker(t.remoteUuid, t.startTime)
        worker?.makeDeclined()

        return WarpProto.VoidType.getDefaultInstance()
    }

    override suspend fun stopTransfer(
        request: StopInfo,
    ): WarpProto.VoidType {
        Log.d(TAG, "Transfer stopped by the other side")
        val t = getTransfer(request.info) ?: return WarpProto.VoidType.getDefaultInstance()

        val worker = transfersManager.getWorker(t.remoteUuid, t.startTime)
        worker?.onStopped(request.error)

        return WarpProto.VoidType.getDefaultInstance()
    }

    override suspend fun ping(request: LookupName): WarpProto.VoidType {
        return WarpProto.VoidType.getDefaultInstance()
    }

    private fun getTransfer(info: OpInfo): Transfer? {
        val remoteUUID = info.ident
        val r: Remote? = repository.remoteListState.value.find { it.uuid == remoteUUID }

        if (r == null) {
            Log.w(TAG, "Could not find corresponding remote")
            return null
        }

        val t = r.transfers.find { it.startTime == info.timestamp }

        if (t == null) {
            Log.w(TAG, "Could not find corresponding transfer for timestamp ${info.timestamp}")
        }
        return t
    }

    companion object {
        var TAG: String = "GRPC"
        private const val MAX_TRIES = 32 // 8 sec
    }
}