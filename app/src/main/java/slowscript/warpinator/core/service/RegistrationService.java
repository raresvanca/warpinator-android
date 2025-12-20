package slowscript.warpinator.core.service;

import android.util.Base64;
import android.util.Log;

import com.google.common.net.InetAddresses;
import com.google.protobuf.ByteString;

import io.grpc.stub.StreamObserver;
import slowscript.warpinator.WarpProto;
import slowscript.warpinator.WarpRegistrationGrpc;
import slowscript.warpinator.core.model.Remote;
import slowscript.warpinator.core.network.Authenticator;
import slowscript.warpinator.core.network.Server;

public class RegistrationService extends WarpRegistrationGrpc.WarpRegistrationImplBase {
    private static final String TAG = "REG_V2";
    @Override
    public void requestCertificate(WarpProto.RegRequest request, StreamObserver<WarpProto.RegResponse> responseObserver) {
        byte[] cert = Authenticator.getBoxedCertificate();
        byte[] sendData = Base64.encode(cert, Base64.DEFAULT);
        Log.v(TAG, "Sending certificate to " + request.getHostname() + " ; IP=" + request.getIp()); // IP can by mine (Linux impl) or remote's
        responseObserver.onNext(WarpProto.RegResponse.newBuilder().setLockedCertBytes(ByteString.copyFrom(sendData)).build());
        responseObserver.onCompleted();
    }

    @Override
    public void registerService(WarpProto.ServiceRegistration req, StreamObserver<WarpProto.ServiceRegistration> responseObserver) {
        Remote r = MainService.remotes.get(req.getServiceId());
        Log.i(TAG, "Service registration from " + req.getServiceId());
        if (r != null) {
            if (r.status != Remote.RemoteStatus.CONNECTED) {
                r.address = InetAddresses.forString(req.getIp());
                r.authPort = req.getAuthPort();
                r.updateFromServiceRegistration(req);
                if (r.status == Remote.RemoteStatus.DISCONNECTED || r.status == Remote.RemoteStatus.ERROR)
                    r.connect();
                else r.updateUI();
            } else Log.w("REG_V2", "Attempted registration from already connected remote");
        } else {
            r = new Remote();
            r.uuid = req.getServiceId();
            r.address = InetAddresses.forString(req.getIp());
            r.authPort = req.getAuthPort();
            r.updateFromServiceRegistration(req);
            Server.current.addRemote(r);
        }
        responseObserver.onNext(Server.current.getServiceRegistrationMsg());
        responseObserver.onCompleted();
    }
}
