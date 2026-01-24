package slowscript.warpinator.core.data

/**
 * Represents the possible outcomes of a manual connection attempt between peers.
 */
sealed interface ManualConnectionResult {
    data object Success : ManualConnectionResult
    data object NotOnSameSubnet : ManualConnectionResult
    data object AlreadyConnected : ManualConnectionResult
    data object RemoteDoesNotSupportManualConnect : ManualConnectionResult
    data class Error(val message: String) : ManualConnectionResult
}