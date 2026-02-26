package slowscript.warpinator.core.model

import slowscript.warpinator.core.model.Transfer.Direction

data class Message(
    val remoteUuid: String,
    val direction: Direction,
    val timestamp: Long,
    val text: String,
)