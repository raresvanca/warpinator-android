package slowscript.warpinator.core.data

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import slowscript.warpinator.core.model.Remote
import slowscript.warpinator.core.model.Remote.RemoteStatus
import slowscript.warpinator.core.model.Transfer
import slowscript.warpinator.core.service.RemotesManager
import slowscript.warpinator.core.service.TransfersManager
import slowscript.warpinator.core.system.PreferenceManager
import slowscript.warpinator.core.utils.Utils.IPInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WarpinatorRepository @Inject constructor(
    val remotesManager: dagger.Lazy<RemotesManager>,
    val transfersManager: dagger.Lazy<TransfersManager>,
    @param:ApplicationContext val appContext: Context,
    @param:ApplicationScope val applicationScope: CoroutineScope,
) {
    // States
    private val _remoteListState = MutableStateFlow<List<Remote>>(emptyList())
    private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.Starting)
    private val _networkState = MutableStateFlow(NetworkState())
    private val _refreshing = MutableStateFlow(false)
    private val _statusMessages = MutableSharedFlow<String>(
        replay = 0, // Don't replay old messages to new subscribers
        extraBufferCapacity = 64, // Allow buffering if UI is busy
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    // Observables
    val remoteListState = _remoteListState.asStateFlow()
    val serviceState = _serviceState.asStateFlow()
    val networkState = _networkState.asStateFlow()
    val refreshingState = _refreshing.asStateFlow()
    val statusMessages = _statusMessages.asSharedFlow()

    val prefs = PreferenceManager(appContext)

    // Service variables
    var currentIPInfo: IPInfo? = null
    val currentIPStr: String?
        get() = currentIPInfo?.address?.hostAddress

    init {
        prefs.loadSettings()
    }

    fun updateServiceState(newState: ServiceState) {
        _serviceState.value = newState
    }

    fun updateNetworkState(newState: NetworkState) {
        _networkState.value = newState
    }

    fun updateNetworkState(function: (NetworkState) -> NetworkState) {
        _networkState.update(function)
    }

    fun setRefresh(refreshing: Boolean) {
        _refreshing.value = refreshing
    }

    fun emitMessage(message: String) {
        _statusMessages.tryEmit(message)
    }


    // Remotes methods
    fun getRemoteFlow(uuid: String): Flow<Remote?> {
        return _remoteListState.map { list ->
            list.find { it.uuid == uuid }
        }.distinctUntilChanged()
    }

    fun getRemoteStatus(uuid: String): RemoteStatus {
        return _remoteListState.value.find { it.uuid == uuid }?.status ?: RemoteStatus.Disconnected
    }


    fun addOrUpdateRemote(newRemote: Remote) {
        _remoteListState.update { currentList ->
            val index = currentList.indexOfFirst { it.uuid == newRemote.uuid }
            val newList = if (index != -1) {
                // Preserve existing transfers
                val existing = currentList[index]
                val merged = newRemote.copy(
                    transfers = existing.transfers,
                    status = existing.status,
                    isFavorite = existing.isFavorite,
                )
                currentList.toMutableList().apply { set(index, merged) }
            } else {
                currentList + newRemote
            }
            sortRemotes(newList)
        }
    }

    fun updateRemoteStatus(uuid: String, status: RemoteStatus) {
        updateRemote(uuid) { it.copy(status = status) }
    }

    fun updateRemotePicture(uuid: String, picture: Bitmap?) {
        updateRemote(uuid) { it.copy(picture = picture) }
    }

    fun clearRemotes() {
        _remoteListState.value = emptyList()
    }

    fun toggleFavorite(uuid: String) {
        updateRemote(uuid) {
            it.copy(isFavorite = prefs.toggleFavorite(uuid))
        }
    }

    fun updateRemote(uuid: String, transform: (Remote) -> Remote) {
        _remoteListState.update { currentList ->
            val newList = currentList.map {
                if (it.uuid == uuid) transform(it) else it
            }
            sortRemotes(newList)
        }
    }

    private fun sortRemotes(list: List<Remote>): List<Remote> {
        return list.sortedWith(
            compareByDescending<Remote> { it.isFavorite }.thenBy {
                it.displayName ?: it.hostname ?: ""
            },
        )
    }

    // Transfers methods
    fun addTransfer(remoteUuid: String, transfer: Transfer) {
        updateRemote(remoteUuid) { remote ->
            val existingTransfers = remote.transfers.filterNot { it.uid == transfer.uid }
            remote.copy(transfers = listOf(transfer) + existingTransfers)
        }
    }

    fun updateTransfer(remoteUuid: String, updatedTransfer: Transfer) {
        _remoteListState.update { currentList ->
            val remoteIndex = currentList.indexOfFirst { it.uuid == remoteUuid }
            if (remoteIndex == -1) return@update currentList

            val remote = currentList[remoteIndex]
            val newTransfers = remote.transfers.map {
                if (it.uid == updatedTransfer.uid) updatedTransfer else it
            }

            val newRemote = remote.copy(transfers = newTransfers)

            val newList = currentList.toMutableList()
            newList[remoteIndex] = newRemote
            newList
        }
    }

    fun acceptTransfer(remoteUuid: String, transfer: Transfer) {
        transfersManager.get().getWorker(remoteUuid, transfer.startTime)?.startReceive()
    }

    fun declineTransfer(remoteUuid: String, transfer: Transfer) {
        transfersManager.get().getWorker(remoteUuid, transfer.startTime)?.declineTransfer()

    }

    fun cancelTransfer(remoteUuid: String, transfer: Transfer) {
        val worker = transfersManager.get().getWorker(remoteUuid, transfer.startTime)
        worker?.stop(error = false) ?: run {
            updateTransfer(remoteUuid, transfer.copy(status = Transfer.Status.Stopped))
        }
    }

    fun retryTransfer(transfer: Transfer) {
        if (transfer.direction == Transfer.Direction.Send) {
            applicationScope.launch {
                transfersManager.get().retrySend(
                    transfer, isDir = false,
                )
            }
        }
    }

    fun clearTransfer(remoteUuid: String, transferUid: String) {
        updateRemote(remoteUuid) { remote ->
            remote.copy(transfers = remote.transfers.filterNot { it.uid == transferUid })
        }
        val transfer =
            remoteListState.value.find { it.uuid == remoteUuid }?.transfers?.find { it.uid == transferUid }
        if (transfer != null) {
            transfersManager.get().removeWorker(remoteUuid, transfer.startTime)
        }
    }

    fun clearAllFinishedTransfers(remoteUuid: String) {
        updateRemote(remoteUuid) { remote ->
            val activeTransfers = remote.transfers.filter {
                it.status is Transfer.Status.Transferring || it.status is Transfer.Status.WaitingPermission || it.status is Transfer.Status.Initializing
            }
            val removedTransfers = remote.transfers - activeTransfers.toSet()
            removedTransfers.forEach { t ->
                transfersManager.get().removeWorker(remoteUuid, t.startTime)
            }

            remote.copy(transfers = activeTransfers)
        }
    }
}