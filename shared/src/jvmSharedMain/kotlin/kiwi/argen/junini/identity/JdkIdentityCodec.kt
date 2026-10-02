package kiwi.argen.junini.identity

import java.io.ByteArrayInputStream
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAKey
import java.security.spec.PKCS8EncodedKeySpec
import javax.security.auth.x500.X500Principal
import kotlin.time.Instant

private const val RSA_ONLY = "Only RSA identities are supported"

/** Reads identities from PEM files using only what the JDK and Android provide. */
class JdkIdentityCodec : IdentityCodec {
    override fun parse(texts: List<String>): ParsedIdentity {
        val blocks = texts.flatMap(::parsePemBlocks)
        val certificates = blocks.filter { it.isCertificate }.map(::readCertificate)
        if (certificates.isEmpty()) throw InvalidIdentityException("No certificate found. Choose the .crt or .pem file.")
        val keyBlock = blocks.firstOrNull { it.isPrivateKey }
            ?: throw InvalidIdentityException("No private key found. Choose the .key file.")
        val (key, pkcs8) = readPrivateKey(keyBlock)
        if (certificates.none { it.publicKey is RSAKey }) throw InvalidIdentityException(RSA_ONLY)

        // Whatever order the files came in, the certificate that belongs to the key is the identity's own.
        val leaf = certificates.firstOrNull { (it.publicKey as? RSAKey)?.modulus == (key as RSAKey).modulus }
            ?: throw InvalidIdentityException("The private key doesn't belong to the certificate")
        val chain = listOf(leaf) + (certificates - leaf)
        return ParsedIdentity(
            credentials = ClientCredentials(
                certificatePem = chain.joinToString(separator = "") { pemEncode("CERTIFICATE", it.encoded) },
                privateKeyPem = pemEncode("PRIVATE KEY", pkcs8),
            ),
            fingerprint = MessageDigest.getInstance("SHA-256").digest(leaf.encoded).toHex(),
            notAfter = Instant.fromEpochMilliseconds(leaf.notAfter.time),
            commonName = leaf.commonName(),
        )
    }

    private fun readCertificate(block: PemBlock): X509Certificate = try {
        CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(block.der)) as X509Certificate
    } catch (e: Exception) {
        throw InvalidIdentityException("Couldn't read the certificate", e)
    }

    /** The key and its PKCS#8 encoding. */
    private fun readPrivateKey(block: PemBlock): Pair<PrivateKey, ByteArray> {
        if (block.encrypted || block.label == "ENCRYPTED PRIVATE KEY") {
            throw InvalidIdentityException("The private key is password-protected. Remove the password and try again.")
        }
        val pkcs8 = when (block.label) {
            "PRIVATE KEY" -> block.der
            "RSA PRIVATE KEY" -> wrapPkcs1(block.der)
            else -> throw InvalidIdentityException(RSA_ONLY)
        }
        return try {
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs8)) to pkcs8
        } catch (e: GeneralSecurityException) {
            // A PKCS#8 key can hold any algorithm; RSA is the only one that fails to decode as RSA in practice.
            throw InvalidIdentityException(if (block.label == "PRIVATE KEY") RSA_ONLY else "Couldn't read the private key", e)
        }
    }
}

/** The certificate chain and private key to hand to the TLS client. */
internal fun ClientCredentials.toKeyAndChain(): Pair<PrivateKey, Array<X509Certificate>> {
    val factory = CertificateFactory.getInstance("X.509")
    val chain = parsePemBlocks(certificatePem).filter { it.isCertificate }
        .map { factory.generateCertificate(ByteArrayInputStream(it.der)) as X509Certificate }
    val keyDer = parsePemBlocks(privateKeyPem).first { it.isPrivateKey }.der
    return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyDer)) to chain.toTypedArray()
}

/** `CN=...` from the subject, or null. Android has no javax.naming, so the RFC 2253 string is read directly. */
private fun X509Certificate.commonName(): String? {
    val subject = subjectX500Principal.getName(X500Principal.RFC2253)
    return Regex("""(?:^|,)CN=((?:\\.|[^,\\])*)""").find(subject)?.groupValues?.get(1)?.replace(Regex("""\\(.)"""), "$1")
}

/** PKCS#1 `RSAPrivateKey` wrapped in the PKCS#8 `PrivateKeyInfo` that `KeyFactory` expects. */
private fun wrapPkcs1(pkcs1: ByteArray): ByteArray {
    val version = byteArrayOf(0x02, 0x01, 0x00)
    // AlgorithmIdentifier { rsaEncryption, NULL }
    val algorithm = byteArrayOf(
        0x30, 0x0d, 0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00,
    )
    return der(0x30, version + algorithm + der(0x04, pkcs1))
}

private fun der(tag: Int, content: ByteArray): ByteArray {
    val length = content.size
    val encodedLength = if (length < 0x80) {
        byteArrayOf(length.toByte())
    } else {
        val bytes = generateSequence(length) { it ushr 8 }.takeWhile { it > 0 }.map { it.toByte() }.toList().reversed()
        byteArrayOf((0x80 or bytes.size).toByte()) + bytes.toByteArray()
    }
    return byteArrayOf(tag.toByte()) + encodedLength + content
}

@OptIn(ExperimentalStdlibApi::class)
private fun ByteArray.toHex(): String = toHexString()
