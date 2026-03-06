package slowscript.warpinator.core.network.messages

import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Composable
import slowscript.warpinator.core.model.ui.UiMessage
import slowscript.warpinator.core.model.ui.UiMessageState

class FailedToStartJmDNS : UiMessage() {
    @Composable
    override fun getState(): UiMessageState {
        return UiMessageState(
            message = "Discovery service failed. Other devices might not see you.",
            duration = SnackbarDuration.Indefinite,
        )
    }
}