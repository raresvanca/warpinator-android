package slowscript.warpinator.core.service


import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import slowscript.warpinator.core.data.WarpinatorRepository
import slowscript.warpinator.core.model.Remote
import slowscript.warpinator.core.model.Transfer
import slowscript.warpinator.core.network.worker.TransferWorker
import slowscript.warpinator.core.system.WarpinatorPowerManager
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransfersManager @Inject constructor(
    private val repository: WarpinatorRepository,
    private val remotesManager: RemotesManager,
    private val powerManager: WarpinatorPowerManager,
) {
    private val activeWorkers = ConcurrentHashMap<String, TransferWorker>()

    suspend fun initiateSend(remote: Remote, uris: List<Uri>, isDir: Boolean) =
        withContext(Dispatchers.IO) {
            val initialTransfer = Transfer(
                remoteUuid = remote.uuid,
                direction = Transfer.Direction.Send,
                uris = uris,
                useCompression = repository.server.get().useCompression,
            )

            startSendWorker(initialTransfer, isDir)
        }

    suspend fun retrySend(existingTransfer: Transfer, isDir: Boolean) =
        withContext(Dispatchers.IO) {
            // Reset status and progress, but keep ID, timestamp, and URIs
            val resetTransfer = existingTransfer.copy(
                status = Transfer.Status.Initializing,
                bytesTransferred = 0,
                bytesPerSecond = 0,
                useCompression = repository.server.get().useCompression,
            )
            startSendWorker(resetTransfer, isDir)
        }

    private suspend fun startSendWorker(transfer: Transfer, isDir: Boolean) {
        val worker = TransferWorker(transfer, repository, remotesManager, powerManager)

        val preparedTransfer = worker.prepareSend(isDir)
        val key = getTransferKey(preparedTransfer.remoteUuid, preparedTransfer.startTime)
        activeWorkers[key] = worker
        remotesManager.getWorker(transfer.remoteUuid)?.startSendTransfer(preparedTransfer)
    }

    fun onIncomingTransferRequest(transfer: Transfer) {
        repository.addTransfer(transfer.remoteUuid, transfer)

        val worker = TransferWorker(transfer, repository, remotesManager, powerManager)
        val key = getTransferKey(transfer.remoteUuid, transfer.startTime)
        activeWorkers[key] = worker

        worker.prepareReceive()
    }

    fun getWorker(remoteUuid: String, startTime: Long): TransferWorker? {
        return activeWorkers[getTransferKey(remoteUuid, startTime)]
    }

    fun removeWorker(remoteUuid: String, startTime: Long) {
        activeWorkers.remove(getTransferKey(remoteUuid, startTime))
    }

    private fun getTransferKey(uuid: String, timestamp: Long): String = "${uuid}_$timestamp"
}