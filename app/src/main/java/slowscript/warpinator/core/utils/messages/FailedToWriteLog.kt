package slowscript.warpinator.core.utils.messages

import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Composable
import slowscript.warpinator.core.model.ui.UiMessage
import slowscript.warpinator.core.model.ui.UiMessageState

class FailedToWriteLog(val exception: Exception) : UiMessage() {
    @Composable
    override fun getState(): UiMessageState {
        return UiMessageState(
            message = "Could not save log to file: " + exception.message,
            duration = SnackbarDuration.Long,
        )
    }
}