package slowscript.warpinator.core.model

import android.graphics.Bitmap
import java.net.InetAddress

data class Remote(
    val uuid: String,
    val address: InetAddress? = null,
    val port: Int = 0,
    val authPort: Int = 0,
    val api: Int = 1,
    val serviceName: String? = null,
    val userName: String = "",
    val hostname: String? = null,
    val displayName: String? = null,
    val picture: Bitmap? = null,

    val status: RemoteStatus = RemoteStatus.Disconnected,
    val serviceAvailable: Boolean = false,
    val staticService: Boolean = false,
    val hasErrorGroupCode: Boolean = false,
    val hasErrorReceiveCert: Boolean = false,

    val transfers: List<Transfer> = emptyList(),
    val messages: List<Message> = emptyList(),

    val supportsTextMessages: Boolean = false,
    val hasUnreadMessages: Boolean = false,

    val isFavorite: Boolean,
) {
    sealed interface RemoteStatus {
        data object Connected : RemoteStatus
        data object Disconnected : RemoteStatus
        data object Connecting : RemoteStatus
        data class Error(
            val message: String = "",
            val hasSslException: Boolean = false,
            val hasGroupCodeException: Boolean = false,
            val isCertificateUnreceived: Boolean = false,
            val isDuplexFailed: Boolean = false,
            val hasUsernameException: Boolean = false,
        ) : RemoteStatus

        data object AwaitingDuplex : RemoteStatus
    }

    object RemoteFeatures {
        const val TEXT_MESSAGES = 1
    }
}