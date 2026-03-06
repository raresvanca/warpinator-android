package slowscript.warpinator.core.utils.messages

import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Composable
import slowscript.warpinator.core.model.ui.UiMessage
import slowscript.warpinator.core.model.ui.UiMessageState

class SucceededToWriteLog : UiMessage() {
    @Composable
    override fun getState(): UiMessageState {
        return UiMessageState(
            message = "Dumped log file to selected destination",
            duration = SnackbarDuration.Short,
        )
    }
}