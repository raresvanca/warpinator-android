package slowscript.warpinator.core.model

import android.net.Uri
import java.util.UUID

data class Transfer(
    val uid: String = UUID.randomUUID().toString(),
    val remoteUuid: String,
    val direction: Direction,
    val status: Status = Status.Initializing,

    // Time
    val startTime: Long = System.currentTimeMillis(),

    // Progress
    val totalSize: Long = 0,
    val bytesTransferred: Long = 0,
    val bytesPerSecond: Long = 0,

    // File Details
    val fileCount: Long = 0,
    val singleFileName: String? = null, // Used if fileCount == 1 or for the top directory
    val singleMimeType: String? = null,
    val overwriteWarning: Boolean = false,
    val topDirBaseNames: List<String> = emptyList(),


    // For Logic
    val useCompression: Boolean = false,
    val uris: List<Uri> = emptyList()
) {
    enum class Direction {
        Send, Receive
    }

    sealed interface Status {
        data object Initializing : Status
        data object WaitingPermission : Status
        data object Transferring : Status
        data object Paused : Status
        data object Stopped : Status
        data object Finished : Status
        data object Declined : Status
        data class Failed(
            val error: Error, val isRecoverable: Boolean
        ) : Status

        data class FinishedWithErrors(
            val errors: List<Error>
        ) : Status
    }

    sealed interface Error {
        data class Generic(val message: String) : Error
        data class ConnectionLost(val details: String?) : Error
        data object StorageFull : Error
        data class FileNotFound(val filename: String) : Error
        data class PermissionDenied(val path: String?) : Error
        data object DownloadDirectoryNotSet : Error
        data object SymlinksNotSupported : Error
    }

    enum class FileType(val value: Int) {
        File(1), Directory(2), Symlink(3)
    }
}