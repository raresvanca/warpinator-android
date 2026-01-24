package slowscript.warpinator.core.utils

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.google.common.net.InetAddresses
import com.google.common.primitives.Ints
import io.grpc.stub.StreamObserver
import slowscript.warpinator.WarpProto
import slowscript.warpinator.core.network.Server
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.net.URLDecoder
import java.net.UnknownHostException
import java.text.CharacterIterator
import java.text.StringCharacterIterator
import java.util.Collections
import java.util.Random
import kotlin.math.abs
import kotlin.math.sign

object Utils {
    private const val TAG = "Utils"

    fun getDeviceName(context: Context): String {
        var name: String? = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) name =
                Settings.Global.getString(
                    context.contentResolver, Settings.Global.DEVICE_NAME
                )
            if (name == null) name = Settings.Secure.getString(
                context.contentResolver, "bluetooth_name"
            )
        } catch (_: Exception) {
        }
        if (name == null) {
            Log.v(
                TAG, "Could not get device name - using default"
            )
            name = "Android Phone"
        }
        return name
    }

    fun iPAddress(context: Context, networkInterface: String?): IPInfo? {
        networkInterface ?: return null
        try {
            if (!networkInterface.isEmpty() && (networkInterface != Server.NETWORK_INTERFACE_AUTO)) {
                val ia = getIPForIfaceName(networkInterface)
                if (ia != null) return ia
                else Log.d(
                    TAG, "Preferred network interface is unavailable, falling back to automatic"
                )
            }
            var ip: IPInfo?
            //Works for most cases
            ip = networkIP(context)
            if (ip == null) ip = wifiIP(context)
            //Try figuring out what interface wifi has, fallback to wlan0 - in case of hotspot
            if (ip == null) ip = getIPForIfaceName(wifiInterface)
            //Get IP of an active interface (except loopback and data)
            if (ip == null) {
                val activeNi: NetworkInterface? = activeIface
                if (activeNi != null) ip = getIPForIface(activeNi)
            }
            Log.v(
                TAG,
                "Got IP: " + (if (ip == null) "null" else (ip.address.hostAddress + "/" + ip.prefixLength))
            )
            return ip
        } catch (ex: Exception) {
            Log.e(
                TAG, "Couldn't get IP address", ex
            )
            return null
        }
    }

    @SuppressLint("WifiManagerPotentialLeak")
    fun wifiIP(context: Context): IPInfo? {
        val wifiManager =
            context.getSystemService(Context.WIFI_SERVICE) as WifiManager? ?: return null
        val ip = wifiManager.connectionInfo.ipAddress
        if (ip == 0) return null
        return try {
            // No way to get prefix length, guess 24
            IPInfo(
                InetAddresses.fromLittleEndianByteArray(
                    Ints.toByteArray(
                        ip
                    )
                ) as Inet4Address, 24
            )
        } catch (_: UnknownHostException) {
            null
        }
    }

    fun networkIP(context: Context): IPInfo? {
        val connMgr =
            checkNotNull(context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
        val activeNetwork = connMgr.activeNetwork
        val networkCaps = connMgr.getNetworkCapabilities(activeNetwork)
        val properties = connMgr.getLinkProperties(activeNetwork)
        if (properties != null && networkCaps != null && networkCaps.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_VPN
            ) && (networkCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || networkCaps.hasTransport(
                NetworkCapabilities.TRANSPORT_ETHERNET
            ))
        ) {
            for (addr in properties.linkAddresses) if (addr.address is Inet4Address) return IPInfo(
                (addr.address as Inet4Address?)!!, addr.prefixLength
            )
        }
        return null
    }

    @get:Throws(SocketException::class)
    val activeIface: NetworkInterface?
        get() {
            val nis: MutableList<NetworkInterface> =
                Collections.list(NetworkInterface.getNetworkInterfaces())
            //prioritize wlan(...) interfaces and deprioritize tun(...) which can be leftover from VPN apps
            Collections.sort(
                nis, Comparator sort@{ i1: NetworkInterface?, i2: NetworkInterface? ->
                    val i1Name = i1!!.displayName
                    val i2Name = i2!!.displayName
                    if (i1Name.contains("wlan") || i2Name.contains("tun")) {
                        return@sort -1
                    } else if (i1Name.contains("tun") || i2Name.contains("wlan")) {
                        return@sort 1
                    }
                    0
                })

            for (ni in nis) {
                if ((!ni.isLoopback) && ni.isUp) {
                    val name = ni.displayName
                    if (name.contains("dummy") || name.contains("rmnet") || name.contains("ifb")) continue
                    if (getIPForIface(ni) == null)  //Skip ifaces with no IPv4 address
                        continue
                    Log.d(
                        TAG, "Selected interface: " + ni.displayName
                    )
                    return ni
                }
            }
            return null
        }

    val wifiInterface: String
        get() {
            var iface: String? = null
            try {
                val m = Class.forName("android.os.SystemProperties")
                    .getMethod("get", String::class.java)
                iface = m.invoke(null, "wifi.interface") as String?
            } catch (ignored: Throwable) {
            }
            if (iface == null || iface.isEmpty()) iface = "wlan0"
            return iface
        }

    val networkInterfaces: Array<String?>?
        get() {
            val nisNames = ArrayList<String?>()
            try {
                val nis = NetworkInterface.getNetworkInterfaces()
                while (nis.hasMoreElements()) {
                    val ni = nis.nextElement()
                    if (ni.isUp) {
                        nisNames.add(ni.displayName)
                    }
                }
            } catch (e: SocketException) {
                Log.e(
                    TAG, "Could not get network interfaces", e
                )
                return null
            }
            return nisNames.toTypedArray<String?>()
        }

    fun dumpInterfaces(): String? {
        val nis: Array<String?> = networkInterfaces ?: return "Failed to get network interfaces"
        return TextUtils.join("\n", nis)
    }

    @Throws(SocketException::class)
    fun getIPForIfaceName(ifaceName: String?): IPInfo? {
        val nis = NetworkInterface.getNetworkInterfaces()
        var ni: NetworkInterface
        while (nis.hasMoreElements()) {
            ni = nis.nextElement()
            if (ni.displayName == ifaceName) {
                return getIPForIface(ni)
            }
        }
        return null
    }

    fun getIPForIface(ni: NetworkInterface): IPInfo? {
        for (ia in ni.interfaceAddresses) {
            //filter for ipv4/ipv6
            if (ia.address.address.size == 4) {
                //4 for ipv4, 16 for ipv6
                return IPInfo(
                    (ia.address as Inet4Address?)!!, ia.networkPrefixLength.toInt()
                )
            }
        }
        return null
    }

    @JvmStatic
    fun isSameSubnet(a: InetAddress, b: InetAddress, prefix: Int): Boolean {
        val aa = a.address
        val ba = b.address
        if (aa.size != ba.size) return false
        var i = 0
        while (i < prefix) {
            val bi = i / 8
            if (bi >= aa.size) break
            val rem = prefix - i
            if (rem >= 8) {
                if (aa[bi] != ba[bi]) return false
            } else {
                val mask = ((0xFF shl (8 - rem)) and 0xFF).toByte()
                if ((aa[bi].toInt() and mask.toInt()) != (ba[bi].toInt() and mask.toInt())) return false
            }
            i += 8
        }
        return true
    }

    @JvmStatic
    fun certsDir(context: Context): File {
        return File(context.cacheDir, "certs")
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readAllBytes(file: File?): ByteArray {
        RandomAccessFile(file, "r").use { f ->
            val b = ByteArray(f.length().toInt())
            f.readFully(b)
            return b
        }
    }

    fun bytesToHumanReadable(bytes: Long): String {
        val absB = if (bytes == Long.MIN_VALUE) Long.MAX_VALUE else abs(bytes)
        if (absB < 1024) {
            return "$bytes B"
        }
        var value = absB
        val ci: CharacterIterator = StringCharacterIterator("KMGTPE")
        var i = 40
        while (i >= 0 && absB > 0xfffccccccccccccL shr i) {
            value = value shr 10
            ci.next()
            i -= 10
        }
        value *= bytes.sign
        return String.format("%.1f %cB", value / 1024.0, ci.current())
    }

    @SuppressLint("Range")
    fun getNameFromUri(ctx: Context, uri: Uri): String? {
        var result: String? = null
        if ("content" == uri.scheme) {
            try {
                val cursor = ctx.contentResolver.query(uri, null, null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                    cursor.close()
                }
            } catch (_: Exception) {
            }
        }
        if (result == null) {
            val parts: Array<String?> = URLDecoder.decode(uri.toString()).split("/".toRegex())
                .dropLastWhile { it.isEmpty() }.toTypedArray()
            return parts[parts.size - 1]
        }
        return result
    }


    fun getChildUri(treeUri: Uri?, path: String?): Uri {
        val rootID = DocumentsContract.getTreeDocumentId(treeUri)
        val docID = "$rootID/$path"
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, docID)
    }

    fun getChildFromTree(ctx: Context, treeUri: Uri?, path: String?): DocumentFile {
        val childUri = getChildUri(treeUri, path)
        return DocumentFile.fromSingleUri(ctx, childUri)!!
    }

    //Just like DocumentFile.exists() but doesn't spam "Failed query" when file is not found
    fun pathExistsInTree(ctx: Context, treeUri: Uri?, path: String?): Boolean {
        val resolver = ctx.contentResolver
        val u = getChildUri(treeUri, path)
        val c: Cursor?
        try {
            c = resolver.query(
                u, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null
            )
            val found = c!!.count > 0
            c.close()
            return found
        } catch (_: Exception) {
        }
        return false
    }

    fun isMyServiceRunning(ctx: Context, serviceClass: Class<*>): Boolean {
        val manager =
            checkNotNull(ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    fun isConnectedToWiFiOrEthernet(ctx: Context): Boolean {
        val connManager =
            checkNotNull(ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
        val wifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
        val ethernet = connManager.getNetworkInfo(ConnectivityManager.TYPE_ETHERNET)
        return (wifi != null && wifi.isConnected) || (ethernet != null && ethernet.isConnected)
    }

    fun isHotspotOn(ctx: Context): Boolean {
        val manager = checkNotNull(
            ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        )
        try {
            val method = manager.javaClass.getDeclaredMethod("isWifiApEnabled")
            method.isAccessible = true //in the case of visibility change in future APIs
            return (method.invoke(manager) as Boolean?)!!
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get hotspot state", e)
        }

        return false
    }

    @JvmStatic
    fun generateServiceName(context: Context): String {
        return getDeviceName(context).uppercase().replace(" ", "") + "-" + getRandomHexString(6)
    }

    fun getRandomHexString(len: Int): String {
        val buf = CharArray(len)
        val random = Random()
        for (idx in buf.indices) buf[idx] = HEX_ARRAY[random.nextInt(HEX_ARRAY.size)]
        return String(buf)
    }

    fun openUrl(context: Context, url: String?) {
        val intent = Intent(Intent.ACTION_VIEW, url?.toUri())
        context.startActivity(intent)
    }

    //FOR DEBUG PURPOSES
    private val HEX_ARRAY = "0123456789ABCDEF".toCharArray()
    fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = HEX_ARRAY[v ushr 4]
            hexChars[j * 2 + 1] = HEX_ARRAY[v and 0x0F]
        }
        return String(hexChars)
    }

    class IPInfo internal constructor(
        @JvmField var address: Inet4Address, @JvmField var prefixLength: Int
    )

    class VoidObserver : StreamObserver<WarpProto.VoidType?> {
        override fun onNext(value: WarpProto.VoidType?) {}
        override fun onError(t: Throwable?) {
            Log.e(TAG, "Call failed with exception", t)
        }

        override fun onCompleted() {}
    }
}
