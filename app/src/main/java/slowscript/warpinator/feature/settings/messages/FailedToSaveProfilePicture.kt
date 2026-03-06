package slowscript.warpinator.feature.settings.messages

import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import slowscript.warpinator.R
import slowscript.warpinator.core.model.ui.UiMessage
import slowscript.warpinator.core.model.ui.UiMessageState

class FailedToSaveProfilePicture(
    val exception: Exception,
) : UiMessage() {
    @Composable
    override fun getState(): UiMessageState {
        return UiMessageState(
            message = stringResource(R.string.failed_to_save_profile_picture, exception),
            duration = SnackbarDuration.Long,
        )

    }
}