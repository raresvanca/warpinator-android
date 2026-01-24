package slowscript.warpinator.core.service

import slowscript.warpinator.core.data.WarpinatorRepository
import slowscript.warpinator.core.model.Remote
import slowscript.warpinator.core.network.Authenticator
import slowscript.warpinator.core.network.worker.RemoteWorker
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemotesManager @Inject constructor(
    private val repository: WarpinatorRepository, private val authenticator: Authenticator
) {
    private val workers = ConcurrentHashMap<String, RemoteWorker>()

    fun onRemoteDiscovered(remote: Remote): RemoteWorker? {
        repository.addOrUpdateRemote(remote)

        if (!workers.containsKey(remote.uuid)) {
            val newWorker = RemoteWorker(
                uuid = remote.uuid, repository = repository, authenticator = authenticator
            )
            workers[remote.uuid] = newWorker
            return newWorker
        }

        return null
    }

    fun getWorker(uuid: String): RemoteWorker? = workers[uuid]
}