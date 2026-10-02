package kiwi.argen.junini.gemini

import java.net.InetAddress
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import kiwi.argen.junini.identity.Fixtures
import kiwi.argen.junini.identity.IdentityManager
import kiwi.argen.junini.identity.InMemoryIdentityStore
import kiwi.argen.junini.identity.JdkIdentityCodec
import kiwi.argen.junini.identity.toKeyAndChain
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** Talks to a real local TLS server that asks for a client certificate, as a Gemini capsule with logins does. */
class ClientCertificateTransportTest {
    private val codec = JdkIdentityCodec()

    /** Answers 20 and the SHA-256 of the client certificate it was shown, or 60 if there was none. */
    private class CapsuleServer(codec: JdkIdentityCodec) : AutoCloseable {
        private val server: SSLServerSocket
        val port: Int get() = server.localPort

        init {
            // The server's own certificate is Bob's; Alice is the client.
            val (serverKey, serverChain) = codec.parse(listOf(Fixtures.bobCertificate, Fixtures.bobKey)).credentials.toKeyAndChain()
            val keyStore = KeyStore.getInstance("PKCS12").apply {
                load(null, null)
                setKeyEntry("server", serverKey, "pw".toCharArray(), serverChain)
            }
            val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                .apply { init(keyStore, "pw".toCharArray()) }.keyManagers
            // Self-signed client certificates are accepted, whoever signed them.
            val acceptAll = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val context = SSLContext.getInstance("TLS").apply { init(keyManagers, arrayOf(acceptAll), null) }
            server = (context.serverSocketFactory.createServerSocket(0, 1, InetAddress.getLoopbackAddress()) as SSLServerSocket)
                .apply { wantClientAuth = true }
            thread(isDaemon = true) {
                while (!server.isClosed) {
                    try {
                        (server.accept() as SSLSocket).use { socket ->
                            socket.startHandshake()
                            val clientCertificate = try {
                                socket.session.peerCertificates.firstOrNull() as? X509Certificate
                            } catch (e: SSLPeerUnverifiedException) {
                                null
                            }
                            socket.inputStream.bufferedReader().readLine()
                            val response = if (clientCertificate == null) {
                                "60 Please identify yourself\r\n"
                            } else {
                                "20 text/gemini\r\n" + sha256(clientCertificate)
                            }
                            socket.outputStream.write(response.encodeToByteArray())
                            socket.outputStream.flush()
                        }
                    } catch (e: Exception) {
                        // A client that gave up mid-handshake, or the server closing.
                    }
                }
            }
        }

        override fun close() = server.close()
    }

    @Test
    fun presentsTheAssignedIdentityToTheServer() = runBlocking {
        CapsuleServer(codec).use { server ->
            val store = InMemoryIdentityStore()
            val manager = IdentityManager(store, codec)
            val identity = manager.import(listOf(Fixtures.aliceCertificate, Fixtures.aliceKey))
            val transport = platformGeminiTransport(KnownHosts(InMemoryKnownHostsStore()), store)

            val without = transport.fetch("localhost", server.port, "gemini://localhost/\r\n").decodeToString()
            assertTrue(without.startsWith("60 "), "expected 60 without an identity but got: $without")

            manager.assign("localhost", server.port, identity.id)
            val with = transport.fetch("localhost", server.port, "gemini://localhost/\r\n").decodeToString()
            assertEquals("20 text/gemini\r\n${identity.fingerprint}", with)
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
private fun sha256(certificate: X509Certificate): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(certificate.encoded).toHexString()
