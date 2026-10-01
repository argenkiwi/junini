package kiwi.argen.junini.gemini

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class FileKnownHostsStoreTest {

    private val dir = Files.createTempDirectory("known-hosts").toFile()
    private val file = File(dir, "nested/known_hosts")
    private val certificate = ServerCertificate("aa11", Instant.fromEpochSeconds(1_900_000_000))

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun pinsSurviveANewInstance() {
        FileKnownHostsStore(file).put("Example.org", 1965, certificate)
        FileKnownHostsStore(file).put("::1", 1966, certificate)

        val reopened = FileKnownHostsStore(file)

        assertEquals(certificate, reopened.get("example.org", 1965))
        assertEquals(certificate, reopened.get("::1", 1966))
        assertNull(reopened.get("example.org", 1966))
    }

    @Test
    fun skipsMalformedLines() {
        file.parentFile.mkdirs()
        file.writeText("garbage\nexample.org notaport aa11 1\nexample.org 1965 aa11 1900000000\n")

        assertEquals(certificate, FileKnownHostsStore(file).get("example.org", 1965))
    }
}
