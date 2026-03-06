package slowscript.warpinator.core.network.messages

import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Composable
import slowscript.warpinator.core.model.ui.UiMessage
import slowscript.warpinator.core.model.ui.UiMessageState

class FoundGroupCodeError : UiMessage() {
    @Composable
    override fun getState(): UiMessageState {
        return UiMessageState(
            message = "Found devices using different group codes. Make sure your current code is correct.",
            duration = SnackbarDuration.Indefinite,
        )
    }

    override val id = "FoundGroupCodeError"
}