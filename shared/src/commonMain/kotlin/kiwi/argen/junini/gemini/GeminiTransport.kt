package kiwi.argen.junini.gemini

import kiwi.argen.junini.identity.IdentityStore

/** Sends one request over TLS and returns every byte the server wrote before closing the connection. */
interface GeminiTransport {
    suspend fun fetch(host: String, port: Int, request: String): ByteArray
}

/**
 * The transport for the current platform. Only the JVM targets (Android, Desktop) have one:
 * Ktor can't do TLS on Native, and browsers can't open raw TCP sockets.
 *
 * Server certificates are checked against [knownHosts] before the request is sent. If [identities] has an
 * identity assigned to the host, its client certificate is offered when the server asks for one.
 */
expect fun platformGeminiTransport(knownHosts: KnownHosts, identities: IdentityStore): GeminiTransport

internal object UnsupportedGeminiTransport : GeminiTransport {
    override suspend fun fetch(host: String, port: Int, request: String): ByteArray =
        throw UnsupportedOperationException("Gemini networking isn't supported on this platform yet")
}
