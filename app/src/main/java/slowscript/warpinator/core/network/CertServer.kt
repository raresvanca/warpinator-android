package slowscript.warpinator.core.network

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CertServer @Inject constructor(
    var authenticator: Authenticator
) {
    var port: Int = 0
    var serverSocket: DatagramSocket? = null
    var running: AtomicBoolean = AtomicBoolean(false)

    suspend fun start(port: Int) = withContext(Dispatchers.IO) {
        this@CertServer.port = port
        stop()

        running.set(true)

        try {
            serverSocket = DatagramSocket(port)
            Log.d(TAG, "CertServer started on port $port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start certificate server", e)
            running.set(false)
            return@withContext
        }

        val cert = authenticator.boxedCertificate
        val sendData = Base64.encode(cert, Base64.DEFAULT)
        val receiveData = ByteArray(1024)

        while (running.get() && isActive) {
            try {
                val receivePacket = DatagramPacket(receiveData, receiveData.size)
                serverSocket?.receive(receivePacket)

                val received = receivePacket.data.copyOfRange(0, receivePacket.length)
                val request = String(received)

                if (request == REQUEST) {
                    val address = receivePacket.address
                    val clientPort = receivePacket.port
                    val sendPacket = DatagramPacket(sendData, sendData.size, address, clientPort)
                    serverSocket?.send(sendPacket)
                    Log.d(TAG, "Certificate sent to $address")
                }
            } catch (e: Exception) {
                if (running.get()) {
                    Log.w(TAG, "Error in CertServer loop: ${e.message}")
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        serverSocket?.close()
        serverSocket = null
    }

    companion object {
        var TAG: String = "CertServer"
        var REQUEST: String = "REQUEST"

    }
}
