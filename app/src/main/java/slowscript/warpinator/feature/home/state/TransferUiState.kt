package slowscript.warpinator.feature.home.state

import android.text.format.Formatter
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.text.BidiFormatter
import slowscript.warpinator.R
import slowscript.warpinator.core.model.Transfer

enum class TransferUiActionButtons {
    AcceptAndDecline, Stop, Cancel, Retry, OpenFolder, None
}

enum class TransferUiProgressIndicator {
    Active, Static, None
}

data class TransferUiState(
    val id: String,
    val title: String,
    val statusText: String,
    val statusLongText: String,
    val totalSize: String,
    val isSending: Boolean,
    val progressFloat: Float,
    val iconColor: Color,
    val actionButtons: TransferUiActionButtons,
    val progressIndicator: TransferUiProgressIndicator,
)

@Composable
fun Transfer.toUiState(): TransferUiState {
    val context = LocalContext.current

    val title = if (this.fileCount == 1L) {
        this.singleFileName ?: "File"
    } else {
        pluralStringResource(R.plurals.transfer_files_count, this.fileCount.toInt(), this.fileCount)
    }

    val bidi = BidiFormatter.getInstance()

    val totalSizeStr = remember(this.totalSize) {
        Formatter.formatFileSize(
            context,
            this.totalSize,
        ).let { bidi.unicodeWrap(it) }
    }
    val transferredSizeStr = remember(this.bytesTransferred) {
        Formatter.formatFileSize(
            context,
            this.bytesTransferred,
        ).let { bidi.unicodeWrap(it) }
    }
    val transferSpeedSizeStr = remember(this.bytesPerSecond) {
        Formatter.formatFileSize(
            context,
            this.bytesPerSecond,
        )
    }

    val transferSpeedStr = stringResource(
        R.string.transfer_speed_fmt,
        transferSpeedSizeStr,
    ).let { bidi.unicodeWrap(it) }


    val (statusText, statusLongText) = this.getStatusStrings(
        transferredSizeStr,
        totalSizeStr,
        transferSpeedStr,
        bidi,
    )

    val progressFloat = if (this.totalSize > 0) {
        this.bytesTransferred.toFloat() / this.totalSize.toFloat()
    } else 0f


    val isSending = this.direction == Transfer.Direction.Send
    val isRetryable =
        isSending && (status is Transfer.Status.Failed || status == Transfer.Status.Stopped || status is Transfer.Status.FinishedWithErrors || status == Transfer.Status.Declined)


    val actionButtons = when {
        status == Transfer.Status.WaitingPermission && !isSending -> TransferUiActionButtons.AcceptAndDecline
        (status == Transfer.Status.WaitingPermission && isSending) -> TransferUiActionButtons.Cancel
        status == Transfer.Status.Transferring -> TransferUiActionButtons.Stop
        isRetryable -> TransferUiActionButtons.Retry
        status == Transfer.Status.Finished && !isSending -> TransferUiActionButtons.OpenFolder
        else -> TransferUiActionButtons.None
    }

    val progressIndicator = when {
        status == Transfer.Status.Transferring -> TransferUiProgressIndicator.Active
        status != Transfer.Status.WaitingPermission && status != Transfer.Status.Declined -> TransferUiProgressIndicator.Static
        else -> TransferUiProgressIndicator.None
    }

    return TransferUiState(
        id = this.uid,
        title = title,
        statusText = statusText,
        statusLongText = statusLongText,
        totalSize = totalSizeStr,
        isSending = isSending,
        progressFloat = progressFloat,
        iconColor = this.getStatusColor(),
        actionButtons = actionButtons,
        progressIndicator = progressIndicator,
    )
}

@Composable
private fun Transfer.getStatusStrings(
    transferredSizeStr: String,
    totalSizeStr: String,
    transferSpeedStr: String,
    bidi: BidiFormatter,
): Pair<String, String> {
    return when (this.status) {
        Transfer.Status.WaitingPermission -> {
            val str = if (this.overwriteWarning) {
                stringResource(R.string.transfer_state_waiting_permission_overwrite_warning)
            } else {
                stringResource(R.string.transfer_state_waiting_permission)
            }
            str to str
        }

        Transfer.Status.Transferring -> {
            val remaining = this.getRemainingTime()
            val remainingString = when {
                remaining == null -> stringResource(R.string.time_indefinite)
                remaining <= 5 -> stringResource(R.string.a_few_seconds)
                remaining < 60 -> pluralStringResource(
                    R.plurals.time_seconds_remaining,
                    remaining,
                    remaining,
                )

                remaining < 3600 -> {
                    val seconds = (remaining % 60).let {
                        pluralStringResource(
                            R.plurals.duration_seconds_short,
                            it,
                            it,
                        )
                    }
                    val minutes = (remaining / 60).let {
                        pluralStringResource(
                            R.plurals.duration_minutes_short,
                            it,
                            it,
                        )
                    }

                    stringResource(R.string.time_details_remaining_fmt, minutes, seconds)
                }

                remaining < 86400 -> {
                    val hours = (remaining / 3600).let {
                        pluralStringResource(
                            R.plurals.duration_hours_short,
                            it,
                            it,
                        )
                    }
                    val minutes = ((remaining % 3600) / 60).let {
                        pluralStringResource(
                            R.plurals.duration_minutes_short,
                            it,
                            it,
                        )
                    }

                    stringResource(R.string.time_details_remaining_fmt, hours, minutes)
                }

                else -> stringResource(R.string.time_over_day)
            }.let { bidi.unicodeWrap(it) }

            val short =
                stringResource(R.string.transfer_short_status_fmt, transferredSizeStr, totalSizeStr)
            val long = stringResource(
                R.string.transfer_long_status_fmt,
                transferredSizeStr,
                totalSizeStr,
                transferSpeedStr,
                remainingString,
            )

            short to long
        }

        Transfer.Status.Declined -> {
            val str = stringResource(R.string.transfer_state_declined)

            str to str
        }

        is Transfer.Status.Failed -> {
            val str = stringResource(R.string.transfer_state_failed)
            val details = status.error

            str to "$str\n$details"
        }

        Transfer.Status.Finished -> {
            val str = stringResource(R.string.transfer_state_finished)

            str to str
        }

        is Transfer.Status.FinishedWithErrors -> {
            val str = stringResource(R.string.transfer_state_finished_with_errors)
            val details = status.errors.joinToString("\n")

            str to "$str\n$details"
        }

        Transfer.Status.Initializing -> {
            val str = stringResource(R.string.transfer_state_init)

            str to str
        }

        Transfer.Status.Paused -> {
            val str = stringResource(R.string.transfer_state_paused)

            str to str
        }

        Transfer.Status.Stopped -> {
            val str = stringResource(R.string.transfer_state_stopped)

            str to str

        }
    }
}

private fun Transfer.getRemainingTime(): Int? {
    val now = System.currentTimeMillis()
    val timeDiff = (now - startTime) / 1000f
    if (timeDiff <= 0) return null

    val avgSpeed = bytesTransferred / timeDiff
    if (avgSpeed <= 0) return null

    val secondsRemaining = ((totalSize - bytesTransferred) / avgSpeed).toInt()
    return secondsRemaining
}

@Composable
private fun Transfer.getStatusColor(): Color {
    return when (status) {
        is Transfer.Status.Failed, is Transfer.Status.FinishedWithErrors -> MaterialTheme.colorScheme.error
        Transfer.Status.Finished -> MaterialTheme.colorScheme.primary
        Transfer.Status.Transferring -> MaterialTheme.colorScheme.tertiary
        else -> LocalContentColor.current
    }
}