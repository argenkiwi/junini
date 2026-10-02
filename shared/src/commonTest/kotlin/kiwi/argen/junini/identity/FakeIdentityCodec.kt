package kiwi.argen.junini.identity

import kotlin.time.Instant

/** Stands in for real certificates: a "certificate" is `fingerprint;commonName;notAfterEpochSeconds`, a "key" is any bytes. */
class FakeIdentityCodec : IdentityCodec {
    override fun parse(texts: List<String>): ParsedIdentity {
        val blocks = texts.flatMap(::parsePemBlocks)
        val certificate = blocks.firstOrNull { it.isCertificate } ?: throw InvalidIdentityException("No certificate found")
        val key = blocks.firstOrNull { it.isPrivateKey } ?: throw InvalidIdentityException("No private key found")
        val (fingerprint, name, notAfter) = certificate.der.decodeToString().split(';')
        return ParsedIdentity(
            credentials = ClientCredentials(pemEncode("CERTIFICATE", certificate.der), pemEncode("PRIVATE KEY", key.der)),
            fingerprint = fingerprint,
            notAfter = Instant.fromEpochSeconds(notAfter.toLong()),
            commonName = name.ifEmpty { null },
        )
    }
}

private val DISTANT_FUTURE = Instant.parse("2099-01-01T00:00:00Z")

/** The PEM text of a fake certificate. [fingerprint] should be at least 16 characters, as it provides the id. */
fun fakeCertificate(fingerprint: String, name: String = "Fake", notAfter: Instant = DISTANT_FUTURE): String =
    pemEncode("CERTIFICATE", "$fingerprint;$name;${notAfter.epochSeconds}".encodeToByteArray())

fun fakeKey(): String = pemEncode("PRIVATE KEY", byteArrayOf(1, 2, 3, 4))

/** Imports a fake identity from one combined file. */
fun IdentityManager.importFake(fingerprint: String, name: String = "Fake"): Identity =
    import(listOf(fakeCertificate(fingerprint, name) + fakeKey()))
