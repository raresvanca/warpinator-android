package slowscript.warpinator.feature.connect_dialog

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.util.Consumer
import slowscript.warpinator.core.utils.transformers.ProtocolAddressInputValidator

@Composable
fun ProtocolLaunchEffect(
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    var showDialog by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf<String?>(null) }


    LaunchedEffect(Unit) {
        val intent = activity?.intent
        val data = intent?.data
        if (data != null && data.scheme == "warpinator") {
            address = data.schemeSpecificPart.removePrefix("//")

            val validIp = ProtocolAddressInputValidator.isValidIp(address!!, false)

            showDialog = validIp

            // Clear data so we don't trigger it again on rotation
            intent.data = null
        }
    }

    DisposableEffect(Unit) {
        val listener = Consumer<Intent> { intent ->
            val data = intent.data
            if (data != null && data.scheme == "warpinator") {
                address = data.schemeSpecificPart.removePrefix("//")
                val validIp = ProtocolAddressInputValidator.isValidIp(address!!, false)

                showDialog = validIp

                intent.data = null
            }
        }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }

    if (showDialog && address != null) {
        ManualConnectionDialog(
            onDismiss = {
                showDialog = false
                address = null
            }, dialogState = ManualConnectionDialogState.Connecting(address!!)
        )
    }
}