package slowscript.warpinator.core.data

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import slowscript.warpinator.core.model.Remote
import slowscript.warpinator.core.model.Transfer
import slowscript.warpinator.core.network.Server
import java.io.File
import javax.inject.Inject

@HiltViewModel
class WarpinatorViewModel @Inject constructor(
    val repository: WarpinatorRepository, private val server: Server,
) : ViewModel() {
    // UI States
    val remoteListState = repository.remoteListState.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList(),
    )

    val serviceState = repository.serviceState.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ServiceState.Starting,
    )
    val networkState = repository.networkState.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000),
        NetworkState(
            // Set isConnected to true so the UI doesn't show the disconnected state before the Server actually cheks connection
            isConnected = true, isHotspot = false,
        ),
    )

    val refreshState = repository.refreshingState.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false,
    )

    val address: String
        get() {
            return "${repository.currentIPStr}:${server.authPort}"
        }

    // Remotes

    fun getRemote(uuid: String?): Flow<Remote?> {
        if (uuid == null) return flowOf(null)
        return repository.getRemoteFlow(uuid)
    }

    fun toggleFavorite(uuid: String) {
        viewModelScope.launch {
            repository.toggleFavorite(uuid)
        }
    }

    fun rescan() {
        server.rescan()
    }

    suspend fun connectToRemoteHost(address: String): ManualConnectionResult {
        return server.tryRegisterWithHost(address)
    }

    // Transfers

    fun sendUris(remote: Remote, uris: List<Uri>, isDir: Boolean) {
        repository.applicationScope.launch {
            val contentResolver = repository.appContext.contentResolver

            for (uri in uris) {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            repository.transfersManager.get().initiateSend(remote, uris, isDir)
        }
    }

    fun acceptTransfer(transfer: Transfer) {
        repository.acceptTransfer(transfer.remoteUuid, transfer)
    }

    fun declineTransfer(transfer: Transfer) {
        repository.declineTransfer(transfer.remoteUuid, transfer)
    }

    fun cancelTransfer(transfer: Transfer) {
        repository.cancelTransfer(transfer.remoteUuid, transfer)
    }

    fun retryTransfer(transfer: Transfer) {
        repository.retryTransfer(transfer)
    }

    fun clearTransfer(transfer: Transfer) {
        repository.clearTransfer(transfer.remoteUuid, transfer.uid)
    }

    fun clearAllFinished(remoteUuid: String) {
        repository.clearAllFinishedTransfers(remoteUuid)
    }


    /**
     * Attempts to open the file or directory associated with a completed transfer.
     *
     * If the transfer consists of multiple files, it attempts to open the download directory.
     * If it is a single file, it attempts to open the file using its MIME type.
     *
     * @return `true` if the intent was successfully started, `false` otherwise (e.g., if the
     * transfer is an outgoing one, the file doesn't exist, or no compatible app is found).
     */
    fun openTransfer(transfer: Transfer): Boolean {
        if (transfer.direction == Transfer.Direction.Send) return false

        val downloadUriStr = server.downloadDirUri ?: return false

        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            if (transfer.fileCount > 1) {
                if (downloadUriStr.startsWith("content:")) {
                    intent.setDataAndType(
                        downloadUriStr.toUri(), DocumentsContract.Document.MIME_TYPE_DIR,
                    )
                } else {
                    File(downloadUriStr)
                    intent.setDataAndType(downloadUriStr.toUri(), "resource/folder")
                }
            } else {
                val filename = transfer.singleFileName!!

                if (downloadUriStr.startsWith("content:")) {
                    val treeUri = downloadUriStr.toUri()
                    val fileDoc = slowscript.warpinator.core.utils.Utils.getChildFromTree(
                        repository.appContext, treeUri, filename,
                    )

                    if (fileDoc.exists()) {
                        intent.setDataAndType(fileDoc.uri, transfer.singleMimeType)
                    } else {
                        return false
                    }
                } else {
                    // File System
                    val file = File(downloadUriStr, filename)
                    if (file.exists()) {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            repository.appContext,
                            "${repository.appContext.packageName}.provider",
                            file,
                        )
                        intent.setDataAndType(uri, transfer.singleMimeType)
                    } else {
                        return false
                    }
                }
            }

            repository.appContext.startActivity(intent)
            return true

        } catch (_: Exception) {
            try {
                val dirIntent = Intent(Intent.ACTION_VIEW)
                dirIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                dirIntent.setDataAndType(
                    downloadUriStr.toUri(), DocumentsContract.Document.MIME_TYPE_DIR,
                )
                repository.appContext.startActivity(dirIntent)
                return true
            } catch (_: Exception) {
                return false
            }
        }
    }
}