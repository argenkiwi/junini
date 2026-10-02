package kiwi.argen.junini.identity

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PemTest {
    private val bytes = ByteArray(200) { it.toByte() }

    @Test
    fun encodeAndParseRoundTrip() {
        val pem = pemEncode("CERTIFICATE", bytes)

        assertTrue(pem.startsWith("-----BEGIN CERTIFICATE-----\n"))
        assertTrue(pem.endsWith("-----END CERTIFICATE-----\n"))
        assertTrue(pem.lines().all { it.length <= 64 || it.startsWith("-----") })
        val block = parsePemBlocks(pem).single()
        assertEquals("CERTIFICATE", block.label)
        assertContentEquals(bytes, block.der)
        assertTrue(block.isCertificate)
        assertFalse(block.isPrivateKey)
    }

    @Test
    fun parsesSeveralBlocksAndIgnoresTextBetweenThem() {
        val text = "# Lagrange identity\n" + pemEncode("CERTIFICATE", bytes) + "some comment\r\n" + pemEncode("PRIVATE KEY", bytes)

        assertEquals(listOf("CERTIFICATE", "PRIVATE KEY"), parsePemBlocks(text).map { it.label })
    }

    @Test
    fun toleratesWindowsLineEndingsAndIndentation() {
        val text = pemEncode("CERTIFICATE", bytes).replace("\n", "\r\n").prependIndent("  ")

        assertContentEquals(bytes, parsePemBlocks(text).single().der)
    }

    @Test
    fun recognisesEveryKindOfPrivateKey() {
        listOf("PRIVATE KEY", "RSA PRIVATE KEY", "EC PRIVATE KEY", "ENCRYPTED PRIVATE KEY").forEach {
            assertTrue(parsePemBlocks(pemEncode(it, bytes)).single().isPrivateKey, it)
        }
    }

    @Test
    fun flagsOldStyleEncryptedKeys() {
        val body = pemEncode("RSA PRIVATE KEY", bytes).lines()
        val text = (listOf(body.first(), "Proc-Type: 4,ENCRYPTED", "DEK-Info: AES-256-CBC,7C7A1ED783A5DFBCE2814A0FA166BD6B", "") + body.drop(1))
            .joinToString("\n")

        val block = parsePemBlocks(text).single()
        assertTrue(block.encrypted)
        assertContentEquals(bytes, block.der)
        assertFalse(parsePemBlocks(pemEncode("RSA PRIVATE KEY", bytes)).single().encrypted)
    }

    @Test
    fun skipsBlocksThatAreNotBase64AndTextWithoutBlocks() {
        assertEquals(emptyList(), parsePemBlocks("-----BEGIN CERTIFICATE-----\n!!!not base64!!!\n-----END CERTIFICATE-----"))
        assertEquals(emptyList(), parsePemBlocks("hello"))
        assertEquals(emptyList(), parsePemBlocks(""))
    }

    @Test
    fun missingPartSaysWhatToAskForNext() {
        val certificate = pemEncode("CERTIFICATE", bytes)
        val key = pemEncode("PRIVATE KEY", bytes)

        assertNull(missingPart(listOf(certificate + key)))
        assertNull(missingPart(listOf(certificate, key)))
        assertEquals(MissingPart.PRIVATE_KEY, missingPart(listOf(certificate)))
        assertEquals(MissingPart.CERTIFICATE, missingPart(listOf(key)))
        assertEquals(MissingPart.CERTIFICATE, missingPart(listOf("nothing")))
    }
}
