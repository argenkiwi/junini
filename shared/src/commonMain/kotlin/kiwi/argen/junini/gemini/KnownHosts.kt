package kiwi.argen.junini.gemini

import kotlin.time.Clock
import kotlin.time.Instant

/** What gets pinned for a host: the SHA-256 of the leaf certificate (lowercase hex) and its expiry. */
data class ServerCertificate(val fingerprint: String, val notAfter: Instant)

/** Pinned certificates, keyed by host and port. */
interface KnownHostsStore {
    fun get(host: String, port: Int): ServerCertificate?
    fun put(host: String, port: Int, certificate: ServerCertificate)
}

/** Forgets everything when the app closes. Used where there's no persistent store (and in tests). */
class InMemoryKnownHostsStore : KnownHostsStore {
    private val entries = mutableMapOf<Pair<String, Int>, ServerCertificate>()

    override fun get(host: String, port: Int): ServerCertificate? = entries[host.lowercase() to port]

    override fun put(host: String, port: Int, certificate: ServerCertificate) {
        entries[host.lowercase() to port] = certificate
    }
}

/** The certificate [presented] by [host] doesn't match the one [pinned] on an earlier visit. */
class CertificateMismatchException(
    val host: String,
    val port: Int,
    val pinned: ServerCertificate,
    val presented: ServerCertificate,
) : GeminiException("The certificate for $host:$port has changed")

/**
 * Trust on first use, as the Gemini spec recommends: Gemini servers mostly use self-signed
 * certificates, so the first certificate seen for a host is pinned and later ones must match it.
 */
class KnownHosts(
    private val store: KnownHostsStore,
    private val now: () -> Instant = { Clock.System.now() },
) {
    /**
     * Pins [presented] on first use and accepts it if it matches the pin. An expired pin is
     * replaced without a warning, since servers have to renew at some point.
     *
     * @throws CertificateMismatchException if a pin that is still valid doesn't match.
     */
    fun verify(host: String, port: Int, presented: ServerCertificate) {
        val pinned = store.get(host, port)
        when {
            pinned == null || pinned.notAfter < now() -> store.put(host, port, presented)
            pinned.fingerprint.equals(presented.fingerprint, ignoreCase = true) -> Unit
            else -> throw CertificateMismatchException(host, port, pinned, presented)
        }
    }

    /** The user chose to trust [certificate], replacing whatever was pinned for [host]. */
    fun trust(host: String, port: Int, certificate: ServerCertificate) = store.put(host, port, certificate)
}

/** Formats a hex fingerprint as colon-separated uppercase byte pairs, e.g. `AB:CD:EF`. */
fun formatFingerprint(hex: String): String = hex.uppercase().chunked(2).joinToString(":")
