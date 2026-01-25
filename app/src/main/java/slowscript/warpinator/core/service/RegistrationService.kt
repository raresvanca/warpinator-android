package slowscript.warpinator.core.service

import android.util.Base64
import android.util.Log
import com.google.common.net.InetAddresses
import com.google.protobuf.ByteString
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.launch
import slowscript.warpinator.WarpProto.RegRequest
import slowscript.warpinator.WarpProto.RegResponse
import slowscript.warpinator.WarpProto.ServiceRegistration
import slowscript.warpinator.WarpRegistrationGrpc.WarpRegistrationImplBase
import slowscript.warpinator.core.data.WarpinatorRepository
import slowscript.warpinator.core.model.Remote
import slowscript.warpinator.core.model.preferences.SavedFavourite
import slowscript.warpinator.core.network.Authenticator
import slowscript.warpinator.core.network.Server
import javax.inject.Inject

class RegistrationService() : WarpRegistrationImplBase() {
    @Inject
    lateinit var repository: WarpinatorRepository

    @Inject
    lateinit var server: Server


    @Inject
    lateinit var remotesManager: RemotesManager

    @Inject
    lateinit var authenticator: Authenticator


    val scope = repository.applicationScope


    override fun requestCertificate(
        request: RegRequest, responseObserver: StreamObserver<RegResponse?>,
    ) {
        val cert = authenticator.boxedCertificate
        val sendData = Base64.encode(cert, Base64.DEFAULT)
        Log.v(
            TAG, "Sending certificate to " + request.getHostname() + " ; IP=" + request.getIp(),
        ) // IP can by mine (Linux impl) or remote's
        responseObserver.onNext(
            RegResponse.newBuilder().setLockedCertBytes(ByteString.copyFrom(sendData)).build(),
        )
        responseObserver.onCompleted()
    }

    override fun registerService(
        req: ServiceRegistration, responseObserver: StreamObserver<ServiceRegistration?>,
    ) {
        val id = req.getServiceId()
        var r: Remote? = repository.remoteListState.value.find { it.uuid == id }
        Log.i(TAG, "Service registration from " + req.getServiceId())
        if (r != null) {
            if (r.status !== Remote.RemoteStatus.Connected) {
                r = r.copy(
                    address = InetAddresses.forString(req.getIp()),
                    authPort = req.authPort,
                    hostname = req.hostname,
                    api = req.apiVersion,
                    port = req.port,
                    serviceName = req.serviceId,
                    staticService = true,
                )
                if (r.status === Remote.RemoteStatus.Disconnected || r.status is Remote.RemoteStatus.Error) {
                    scope.launch {
                        remotesManager.getWorker(r.uuid)?.connect(
                            hostname = r.hostname,
                            address = r.address,
                            authPort = r.authPort,
                            port = r.port,
                            api = r.api,
                        )
                    }
                } else {
                    repository.addOrUpdateRemote(r)
                }
            } else Log.w("REG_V2", "Attempted registration from already connected remote")
        } else {
            r = Remote(
                uuid = req.serviceId,
                address = InetAddresses.forString(req.getIp()),
                authPort = req.authPort,
                hostname = req.hostname,
                api = req.apiVersion,
                port = req.port,
                serviceName = req.serviceId,
                staticService = true,
                isFavorite = repository.prefs.favourites.contains(SavedFavourite(req.serviceId)),
            )
            scope.launch { server.addRemote(r) }
        }
        responseObserver.onNext(server.serviceRegistrationMsg)
        responseObserver.onCompleted()
    }

    companion object {
        private const val TAG = "REG_V2"
    }
}
