package kiwi.argen.junini.identity

import java.security.MessageDigest
import java.security.interfaces.RSAPrivateKey
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalStdlibApi::class)
class JdkIdentityCodecTest {
    private val codec = JdkIdentityCodec()

    private fun failure(vararg texts: String): String =
        assertFailsWith<InvalidIdentityException> { codec.parse(texts.toList()) }.message.orEmpty()

    @Test
    fun readsACertificateAndPkcs8KeyFromTwoFiles() {
        val parsed = codec.parse(listOf(Fixtures.aliceCertificate, Fixtures.aliceKey))

        assertEquals("Alice", parsed.commonName)
        val der = parsePemBlocks(Fixtures.aliceCertificate).single().der
        assertEquals(MessageDigest.getInstance("SHA-256").digest(der).toHexString(), parsed.fingerprint)
        assertTrue(parsed.notAfter > Instant.parse("2100-01-01T00:00:00Z"))
        assertEquals(listOf("CERTIFICATE"), parsePemBlocks(parsed.credentials.certificatePem).map { it.label })
        assertEquals(listOf("PRIVATE KEY"), parsePemBlocks(parsed.credentials.privateKeyPem).map { it.label })
    }

    @Test
    fun readsALegacyPkcs1KeyAndStoresItAsPkcs8() {
        val pkcs1 = codec.parse(listOf(Fixtures.aliceCertificate, Fixtures.read("alice.pkcs1.key")))
        val pkcs8 = codec.parse(listOf(Fixtures.aliceCertificate, Fixtures.aliceKey))

        assertContains(Fixtures.read("alice.pkcs1.key"), "BEGIN RSA PRIVATE KEY")
        assertContentEquals(
            parsePemBlocks(pkcs8.credentials.privateKeyPem).single().der,
            parsePemBlocks(pkcs1.credentials.privateKeyPem).single().der,
        )
    }

    @Test
    fun readsOneFileWithBothAndInAnyOrder() {
        val certificateFirst = codec.parse(listOf(Fixtures.aliceCertificate + "\n" + Fixtures.aliceKey))
        val keyFirst = codec.parse(listOf(Fixtures.aliceKey, "# comment\n" + Fixtures.aliceCertificate))

        assertEquals(certificateFirst.fingerprint, keyFirst.fingerprint)
        assertEquals("Alice", keyFirst.commonName)
    }

    @Test
    fun theCertificateThatMatchesTheKeyBecomesTheLeaf() {
        val parsed = codec.parse(listOf(Fixtures.bobCertificate + Fixtures.aliceCertificate, Fixtures.aliceKey))

        assertEquals("Alice", parsed.commonName)
        assertEquals(2, parsePemBlocks(parsed.credentials.certificatePem).size)
        val (_, chain) = parsed.credentials.toKeyAndChain()
        assertEquals("CN=Alice", chain.first().subjectX500Principal.name)
        assertEquals("CN=Bob", chain.last().subjectX500Principal.name)
    }

    @Test
    fun credentialsTurnIntoAnRsaKeyAndChainForTheTlsClient() {
        val (key, chain) = codec.parse(listOf(Fixtures.aliceCertificate, Fixtures.aliceKey)).credentials.toKeyAndChain()

        assertTrue(key is RSAPrivateKey)
        assertEquals(1, chain.size)
        assertEquals(key.modulus, (chain.single().publicKey as java.security.interfaces.RSAPublicKey).modulus)
    }

    @Test
    fun certificateWithoutACommonNameHasNoName() {
        assertNull(codec.parse(listOf(Fixtures.read("nocn.crt"), Fixtures.read("nocn.key"))).commonName)
    }

    @Test
    fun rejectsEcIdentities() {
        assertContains(failure(Fixtures.read("ec.crt"), Fixtures.read("ec.key")), "Only RSA")
        // An RSA certificate with an EC key, and the other way around, are caught the same way.
        assertContains(failure(Fixtures.aliceCertificate, Fixtures.read("ec.key")), "Only RSA")
        assertContains(failure(Fixtures.read("ec.crt"), Fixtures.aliceKey), "Only RSA")
    }

    @Test
    fun rejectsPasswordProtectedKeys() {
        assertContains(failure(Fixtures.aliceCertificate, Fixtures.read("alice.encrypted.key")), "password")
        assertContains(failure(Fixtures.aliceCertificate, Fixtures.read("alice.legacy-encrypted.key")), "password")
    }

    @Test
    fun rejectsAKeyThatDoesNotBelongToTheCertificate() {
        assertContains(failure(Fixtures.aliceCertificate, Fixtures.bobKey), "doesn't belong")
    }

    @Test
    fun saysWhatIsMissing() {
        assertContains(failure(Fixtures.aliceCertificate), "No private key")
        assertContains(failure(Fixtures.aliceKey), "No certificate")
        assertContains(failure("hello"), "No certificate")
        assertContains(failure(), "No certificate")
    }

    @Test
    fun rejectsACertificateThatIsNotACertificate() {
        assertContains(failure(pemEncode("CERTIFICATE", byteArrayOf(1, 2, 3)), Fixtures.aliceKey), "certificate")
    }
}
