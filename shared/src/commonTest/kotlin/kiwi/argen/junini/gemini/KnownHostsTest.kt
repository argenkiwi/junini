package kiwi.argen.junini.gemini

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class KnownHostsTest {

    private val now = Instant.parse("2026-10-01T00:00:00Z")
    private val original = ServerCertificate("aa11", now + 365.days)
    private val replacement = ServerCertificate("bb22", now + 730.days)

    private class CountingStore : KnownHostsStore {
        private val delegate = InMemoryKnownHostsStore()
        var writes = 0

        override fun get(host: String, port: Int) = delegate.get(host, port)

        override fun put(host: String, port: Int, certificate: ServerCertificate) {
            writes++
            delegate.put(host, port, certificate)
        }
    }

    private val store = CountingStore()
    private val knownHosts = KnownHosts(store, now = { now })

    @Test
    fun pinsCertificateOnFirstUse() {
        knownHosts.verify("example.org", 1965, original)

        assertEquals(original, store.get("example.org", 1965))
    }

    @Test
    fun acceptsMatchingCertificateWithoutRewritingThePin() {
        knownHosts.verify("example.org", 1965, original)

        knownHosts.verify("example.org", 1965, original.copy(fingerprint = "AA11"))

        assertEquals(1, store.writes)
    }

    @Test
    fun rejectsMismatchAndKeepsThePin() {
        knownHosts.verify("example.org", 1965, original)

        val error = assertFailsWith<CertificateMismatchException> {
            knownHosts.verify("example.org", 1965, replacement)
        }

        assertEquals(original, error.pinned)
        assertEquals(replacement, error.presented)
        assertEquals(original, store.get("example.org", 1965))
    }

    @Test
    fun pinsArePerHostAndPort() {
        knownHosts.verify("example.org", 1965, original)

        knownHosts.verify("example.org", 1966, replacement)
        knownHosts.verify("other.example", 1965, replacement)

        assertEquals(original, store.get("example.org", 1965))
    }

    @Test
    fun replacesExpiredPinSilently() {
        val expired = ServerCertificate("aa11", now - 1.days)
        store.put("example.org", 1965, expired)

        knownHosts.verify("example.org", 1965, replacement)

        assertEquals(replacement, store.get("example.org", 1965))
    }

    @Test
    fun trustOverwritesThePin() {
        knownHosts.verify("example.org", 1965, original)

        knownHosts.trust("example.org", 1965, replacement)
        knownHosts.verify("example.org", 1965, replacement)

        assertEquals(replacement, store.get("example.org", 1965))
    }

    @Test
    fun formatsFingerprintAsColonSeparatedPairs() {
        assertEquals("AB:CD:01", formatFingerprint("abcd01"))
    }
}
