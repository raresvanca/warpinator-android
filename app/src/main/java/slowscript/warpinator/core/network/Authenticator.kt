package slowscript.warpinator.core.network

import android.util.Base64
import android.util.Log
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.Time
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.util.io.pem.PemReader
import org.openjax.security.nacl.TweetNaclFast
import slowscript.warpinator.core.data.WarpinatorRepository
import slowscript.warpinator.core.utils.Utils
import slowscript.warpinator.core.utils.Utils.readAllBytes
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.io.IOException
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

@Singleton
class Authenticator @Inject constructor(
    private val repository: WarpinatorRepository
) {
    companion object {
        private const val TAG = "AUTH"
        const val DEFAULT_GROUP_CODE = "Warpinator"
        private const val DAY: Long = 1000L * 60L * 60L * 24
        private const val EXPIRATION_DELTA: Long = 30L * DAY
        const val CERTIFICATE_HEADER: String = "-----BEGIN CERTIFICATE-----\n"
        const val CERTIFICATE_FOOTER: String = "-----END CERTIFICATE-----"
    }

    var groupCode: String = DEFAULT_GROUP_CODE

    var certException: Exception? = null

    val boxedCertificate: ByteArray
        get() {
            var bytes = ByteArray(0)
            try {
                val md = MessageDigest.getInstance("SHA-256")

                val key = md.digest(
                    groupCode.toByteArray(StandardCharsets.UTF_8)
                )
                val box = TweetNaclFast.SecretBox(key)
                val nonce = TweetNaclFast.makeSecretBoxNonce()
                val res = box.box(
                    serverCertificate, nonce
                )

                bytes = ByteArray(24 + res.size)
                System.arraycopy(nonce, 0, bytes, 0, 24)
                System.arraycopy(res, 0, bytes, 24, res.size)
            } catch (e: Exception) {
                Log.wtf(
                    TAG, "WADUHEK", e
                )
            } //This shouldn't fail

            return bytes
        }

    val serverCertificate: ByteArray?
        get() {
            val serverIP: String? = repository.currentIPStr
            //Try loading it first
            try {
                Log.d(
                    TAG, "Loading server certificate..."
                )
                certException = null
                val f = getCertificateFile(".self")
                val cert = getX509fromFile(f)
                cert.checkValidity() //Will throw if expired (and we generate a new one)
                val ip =
                    (cert.subjectAlternativeNames.toTypedArray()[0] as MutableList<*>)[1] as String
                if (ip != serverIP) throw Exception() //Throw if IPs don't match (and regenerate cert)


                return readAllBytes(f)
            } catch (_: Exception) {
            }

            //Create new one if doesn't exist yet
            return createCertificate(
                Utils.getDeviceName(repository.appContext), serverIP
            )
        }

    fun getCertificateFile(hostname: String): File {
        val certsDir: File = Utils.certsDir(repository.appContext)
        return File(certsDir, "$hostname.pem")
    }

    fun createCertificate(hostname: String, ip: String?): ByteArray? {
        var hostname = hostname
        try {
            Log.d(TAG, "Creating new server certificate...")

            Security.addProvider(BouncyCastleProvider())
            //Create KeyPair
            val kp = createKeyPair()

            val now = System.currentTimeMillis()

            //Only allowed chars
            hostname = hostname.replace("[^a-zA-Z0-9]".toRegex(), "")
            if (hostname.trim { it <= ' ' }.isEmpty()) hostname = "android"
            //Build certificate
            val name = X500Name("CN=$hostname")
            val serial = BigInteger(now.toString()) //Use current time as serial num
            val notBefore = Time(Date(now - DAY), Locale.ENGLISH)
            val notAfter = Time(Date(now + EXPIRATION_DELTA), Locale.ENGLISH)

            val builder = JcaX509v3CertificateBuilder(
                name, serial, notBefore, notAfter, name, kp.public
            )
            builder.addExtension(
                Extension.subjectAlternativeName,
                true,
                GeneralNames(GeneralName(GeneralName.iPAddress, ip))
            )

            //Sign certificate
            val signer = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
            val cert = builder.build(signer)

            //Save private key
            val privateKeyBytes = kp.private.encoded
            saveCertOrKey(".self.key-pem", privateKeyBytes, true)

            //Save cert
            val certBytes = cert.encoded
            saveCertOrKey(".self.pem", certBytes, false)

            return certBytes
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create certificate", e)
            certException = e
            return null
        }
    }

    fun saveBoxedCert(bytes: ByteArray, remoteUuid: String?): Boolean {
        try {
            val md = MessageDigest.getInstance("SHA-256")

            val key = md.digest(groupCode.toByteArray(charset("UTF-8")))
            val box = TweetNaclFast.SecretBox(key)
            val nonce = ByteArray(24)
            val cipher = ByteArray(bytes.size - 24)
            System.arraycopy(bytes, 0, nonce, 0, 24)
            System.arraycopy(bytes, 24, cipher, 0, bytes.size - 24)
            val cert = box.open(cipher, nonce)
            if (cert == null) {
                Log.w(TAG, "Failed to unbox cert. Wrong group code?")
                return false
            }

            saveCertOrKey("$remoteUuid.pem", cert, false)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unbox and save certificate", e)
            return false
        }
    }

    private fun saveCertOrKey(filename: String, bytes: ByteArray?, isPrivateKey: Boolean) {
        val certsDir: File = Utils.certsDir(repository.appContext)
        if (!certsDir.exists()) certsDir.mkdir()
        val cert = File(certsDir, filename)

        var begin = CERTIFICATE_HEADER
        var end = CERTIFICATE_FOOTER
        if (isPrivateKey) {
            begin = "-----BEGIN PRIVATE KEY-----\n"
            end = "-----END PRIVATE KEY-----"
        }
        val cert64 = Base64.encodeToString(bytes, Base64.DEFAULT)
        val certString = begin + cert64 + end
        try {
            FileOutputStream(cert, false).use { stream ->
                stream.write(certString.toByteArray())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save certificate or private key: $filename", e)
        }
    }

    @Throws(GeneralSecurityException::class, IOException::class)
    private fun getX509fromFile(f: File?): X509Certificate {
        val fileReader = FileReader(f)
        val pemReader = PemReader(fileReader)
        val obj = pemReader.readPemObject()
        pemReader.close()
        val result: X509Certificate
        ByteArrayInputStream(obj.content).use { `in` ->
            result =
                CertificateFactory.getInstance("X.509").generateCertificate(`in`) as X509Certificate
        }
        return result
    }

    @Throws(NoSuchAlgorithmException::class)
    private fun createKeyPair(algorithm: String = "RSA", bitCount: Int = 2048): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(algorithm)
        keyPairGenerator.initialize(bitCount, SecureRandom())

        return keyPairGenerator.genKeyPair()
    }

    @Throws(GeneralSecurityException::class, IOException::class)
    fun createSSLSocketFactory(name: String): SSLSocketFactory? {
        val crtFile = getCertificateFile(name)

        val sslContext = SSLContext.getInstance("SSL")

        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType())
        trustStore.load(null, null)

        // Read the certificate from disk
        val cert = getX509fromFile(crtFile)

        // Add it to the trust store
        trustStore.setCertificateEntry(crtFile.name, cert)

        // Convert the trust store to trust managers
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(trustStore)
        val trustManagers = tmf.trustManagers

        sslContext.init(null, trustManagers, null)
        return sslContext.socketFactory
    }
}
