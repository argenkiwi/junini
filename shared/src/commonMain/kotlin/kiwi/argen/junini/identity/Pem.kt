package kiwi.argen.junini.identity

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** One `-----BEGIN label-----` section of a PEM file, decoded. [encrypted] is set for old-style password-protected keys. */
class PemBlock(val label: String, val der: ByteArray, val encrypted: Boolean = false) {
    val isCertificate: Boolean get() = label == "CERTIFICATE"

    /** Any kind of private key: `PRIVATE KEY` (PKCS#8), `RSA PRIVATE KEY` (PKCS#1), `EC PRIVATE KEY`, `ENCRYPTED PRIVATE KEY`. */
    val isPrivateKey: Boolean get() = label.endsWith("PRIVATE KEY")
}

// [\s\S] rather than the DOT_MATCHES_ALL option, which Kotlin/JS doesn't have.
private val BLOCK = Regex("-----BEGIN ([A-Z0-9 ]+)-----([\\s\\S]*?)-----END \\1-----")

/** The PEM blocks in [text], in order. Anything between them (Lagrange adds comments) is ignored, as are blocks that aren't valid Base64. */
@OptIn(ExperimentalEncodingApi::class)
fun parsePemBlocks(text: String): List<PemBlock> = BLOCK.findAll(text).mapNotNull { match ->
    val label = match.groupValues[1]
    val lines = match.groupValues[2].lines().map(String::trim).filter(String::isNotEmpty)
    // Old-style encrypted keys have headers such as `Proc-Type: 4,ENCRYPTED` before the Base64 body.
    val headers = lines.filter { ':' in it }
    val body = (lines - headers.toSet()).joinToString("")
    val der = runCatching { Base64.decode(body) }.getOrNull() ?: return@mapNotNull null
    PemBlock(label, der, encrypted = headers.any { it.startsWith("Proc-Type") && "ENCRYPTED" in it })
}.toList()

/** [der] as a PEM block with 64-character lines. */
@OptIn(ExperimentalEncodingApi::class)
fun pemEncode(label: String, der: ByteArray): String =
    Base64.encode(der).chunked(64).joinToString(separator = "\n", prefix = "-----BEGIN $label-----\n", postfix = "\n-----END $label-----\n")

/** What an import is still missing after reading some files. */
enum class MissingPart { CERTIFICATE, PRIVATE_KEY }

/** Whether the files read so far have both a certificate and a private key, and if not, which one to ask for next. */
fun missingPart(texts: List<String>): MissingPart? {
    val blocks = texts.flatMap(::parsePemBlocks)
    return when {
        blocks.none { it.isCertificate } -> MissingPart.CERTIFICATE
        blocks.none { it.isPrivateKey } -> MissingPart.PRIVATE_KEY
        else -> null
    }
}
