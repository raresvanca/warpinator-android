package slowscript.warpinator.core.data

/**
 * Represents the various states of the background service.
 */
sealed interface ServiceState {
    data object Ok : ServiceState
    data object Starting : ServiceState
    data object Stopping : ServiceState
    data object NetworkChangeRestart : ServiceState
    data class InitializationFailed(val interfaces: String?, val exception: String) : ServiceState
}