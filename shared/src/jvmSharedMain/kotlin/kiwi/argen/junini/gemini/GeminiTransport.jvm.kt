package kiwi.argen.junini.gemini

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.tls.tls
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writeStringUtf8
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray

actual fun platformGeminiTransport(knownHosts: KnownHosts): GeminiTransport = KtorGeminiTransport(knownHosts)

private val REQUEST_TIMEOUT = 30.seconds
private const val MAX_RESPONSE_BYTES = 32L * 1024 * 1024

// When the handshake is aborted (e.g. a pinned certificate doesn't match), the TLS session's background
// coroutines fail too, with errors like ClosedWriteChannelException. The caller already gets the real
// error from fetch(), and left unhandled these would crash the app on Android.
private val IgnoreTlsSessionErrors = CoroutineExceptionHandler { _, _ -> }

internal class KtorGeminiTransport(private val knownHosts: KnownHosts) : GeminiTransport {
    override suspend fun fetch(host: String, port: Int, request: String): ByteArray =
        withContext(Dispatchers.IO) {
            val trustManager = TofuTrustManager(knownHosts, host, port)
            // The TLS session launches long-lived reader/writer coroutines in the context it's given.
            // Giving it the caller's context would make this scope wait on them forever, so it gets
            // its own Job, which is cancelled once the response has been read.
            val tlsJob = Job()
            try {
                withTimeout(REQUEST_TIMEOUT) {
                    SelectorManager(Dispatchers.IO).use { selector ->
                        aSocket(selector).tcp().connect(host, port).use { tcp ->
                            tcp.tls(Dispatchers.IO + tlsJob + IgnoreTlsSessionErrors) {
                                serverName = host
                                this.trustManager = trustManager
                            }.use { socket ->
                                val output = socket.openWriteChannel(autoFlush = false)
                                output.writeStringUtf8(request)
                                output.flush()
                                // The server closes the connection once the whole body has been sent.
                                socket.openReadChannel().readRemaining(MAX_RESPONSE_BYTES).readByteArray()
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Ktor wraps whatever the trust manager throws, so the mismatch is recovered from it directly.
                throw trustManager.mismatch ?: e
            } finally {
                tlsJob.cancel()
            }
        }
}

/** Checks the server's leaf certificate against [knownHosts] during the handshake, before any request is sent. */
private class TofuTrustManager(
    private val knownHosts: KnownHosts,
    private val host: String,
    private val port: Int,
) : X509TrustManager {
    @Volatile
    var mismatch: CertificateMismatchException? = null
        private set

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        throw CertificateException("Client certificates aren't checked")

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("The server sent no certificate")
        try {
            knownHosts.verify(host, port, leaf.toServerCertificate())
        } catch (e: CertificateMismatchException) {
            mismatch = e
            throw CertificateException(e.message, e)
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

@OptIn(ExperimentalStdlibApi::class)
private fun X509Certificate.toServerCertificate() = ServerCertificate(
    fingerprint = MessageDigest.getInstance("SHA-256").digest(encoded).toHexString(),
    notAfter = Instant.fromEpochMilliseconds(notAfter.time),
)
