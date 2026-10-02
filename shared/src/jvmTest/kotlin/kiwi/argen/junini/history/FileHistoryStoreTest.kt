package kiwi.argen.junini.history

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Instant

class FileHistoryStoreTest {

    private val dir = Files.createTempDirectory("history").toFile()
    private val file = File(dir, "nested/history")
    private val at = Instant.fromEpochSeconds(1_900_000_000)

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun visitsSurviveANewInstance() {
        FileHistoryStore(file).put(Visit("gemini://example.org/", 3, at))
        FileHistoryStore(file).put(Visit("gemini://[::1]:1966/a b?q", 1, at))

        assertEquals(
            setOf(Visit("gemini://example.org/", 3, at), Visit("gemini://[::1]:1966/a b?q", 1, at)),
            FileHistoryStore(file).all().toSet(),
        )
    }

    @Test
    fun removeDropsOneEntry() {
        val store = FileHistoryStore(file)
        store.put(Visit("gemini://example.org/", 1, at))
        store.put(Visit("gemini://other.net/", 1, at))
        store.remove("gemini://example.org/")

        assertEquals(listOf(Visit("gemini://other.net/", 1, at)), FileHistoryStore(file).all())
    }

    @Test
    fun skipsMalformedLines() {
        file.parentFile.mkdirs()
        file.writeText("garbage\nx 1 gemini://bad/\n2 1900000000\n2 1900000000 gemini://example.org/\n")

        assertEquals(listOf(Visit("gemini://example.org/", 2, at)), FileHistoryStore(file).all())
    }

    @Test
    fun clearDeletesTheFile() {
        val store = FileHistoryStore(file)
        store.put(Visit("gemini://example.org/", 1, at))
        store.clear()

        assertFalse(file.exists())
        assertEquals(emptyList(), store.all())
        assertEquals(emptyList(), FileHistoryStore(file).all())
    }
}
