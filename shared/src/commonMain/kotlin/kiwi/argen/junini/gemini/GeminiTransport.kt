package kiwi.argen.junini.gemini

/** Sends one request over TLS and returns every byte the server wrote before closing the connection. */
interface GeminiTransport {
    suspend fun fetch(host: String, port: Int, request: String): ByteArray
}

/**
 * The transport for the current platform. Only the JVM targets (Android, Desktop) have one:
 * Ktor can't do TLS on Native, and browsers can't open raw TCP sockets.
 */
expect fun platformGeminiTransport(): GeminiTransport

internal object UnsupportedGeminiTransport : GeminiTransport {
    override suspend fun fetch(host: String, port: Int, request: String): ByteArray =
        throw UnsupportedOperationException("Gemini networking isn't supported on this platform yet")
}
