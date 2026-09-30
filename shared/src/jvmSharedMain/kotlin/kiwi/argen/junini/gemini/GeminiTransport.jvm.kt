package kiwi.argen.junini.gemini

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.tls.tls
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writeStringUtf8
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray

actual fun platformGeminiTransport(): GeminiTransport = KtorGeminiTransport

private val REQUEST_TIMEOUT = 30.seconds
private const val MAX_RESPONSE_BYTES = 32L * 1024 * 1024

internal object KtorGeminiTransport : GeminiTransport {
    override suspend fun fetch(host: String, port: Int, request: String): ByteArray =
        withContext(Dispatchers.IO) {
            // The TLS session launches long-lived reader/writer coroutines in the context it's given.
            // Giving it the caller's context would make this scope wait on them forever, so it gets
            // its own Job, which is cancelled once the response has been read.
            val tlsJob = Job()
            try {
                withTimeout(REQUEST_TIMEOUT) {
                    SelectorManager(Dispatchers.IO).use { selector ->
                        aSocket(selector).tcp().connect(host, port).use { tcp ->
                            tcp.tls(Dispatchers.IO + tlsJob) {
                                serverName = host
                                trustManager = AcceptAllTrustManager
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
            } finally {
                tlsJob.cancel()
            }
        }
}

/**
 * Gemini capsules mostly use self-signed certificates. Iteration 1 accepts every certificate;
 * TOFU (pinning the first certificate seen for each host) replaces this later.
 */
private object AcceptAllTrustManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
