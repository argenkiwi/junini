package kiwi.argen.junini.gemini

import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GeminiProtocolTest {
    private val url = Url("gemini://example.org/")

    @Test
    fun parsesHeaderAndBodyOffset() {
        val raw = "20 text/gemini; charset=utf-8\r\n# Hello".encodeToByteArray()

        val (header, bodyStart) = parseHeader(raw)

        assertEquals(GeminiHeader(20, "text/gemini; charset=utf-8"), header)
        assertEquals("# Hello", raw.decodeToString(bodyStart, raw.size))
    }

    @Test
    fun acceptsBareLfAndMissingMeta() {
        val (header, bodyStart) = parseHeader("51\n".encodeToByteArray())

        assertEquals(GeminiHeader(51, ""), header)
        assertEquals(3, bodyStart)
    }

    @Test
    fun rejectsMalformedHeaders() {
        assertFailsWith<GeminiException> { parseHeader("hello\r\n".encodeToByteArray()) }
        assertFailsWith<GeminiException> { parseHeader("20text/gemini\r\n".encodeToByteArray()) }
        assertFailsWith<GeminiException> { parseHeader("20 text/gemini".encodeToByteArray()) }
    }

    @Test
    fun requestLineEndsWithCrlf() {
        assertEquals("gemini://example.org/\r\n", requestLine(url))
    }

    @Test
    fun requestLineRejectsLongUrls() {
        val long = Url("gemini://example.org/" + "a".repeat(MAX_REQUEST_URL_BYTES))
        assertFailsWith<GeminiException> { requestLine(long) }
    }

    @Test
    fun mapsEachStatusClass() {
        fun response(status: Int, meta: String = "") = toResponse(url, GeminiHeader(status, meta), ByteArray(0))

        assertIs<GeminiResponse.Input>(response(10, "Search")).also { assertEquals("Search", it.prompt) }
        assertTrue(assertIs<GeminiResponse.Input>(response(11)).sensitive)
        assertTrue(assertIs<GeminiResponse.Success>(response(20)).mimeType.isGemtext)
        assertIs<GeminiResponse.Redirect>(response(31, "/new")).also {
            assertEquals("/new", it.target)
            assertTrue(it.permanent)
        }
        assertEquals("Not found", assertIs<GeminiResponse.Failure>(response(51)).message)
        assertEquals("Custom", assertIs<GeminiResponse.Failure>(response(40, "Custom")).message)
        assertIs<GeminiResponse.Failure>(response(60))
        assertFailsWith<GeminiException> { response(99) }
    }

    @Test
    fun parsesMimeTypes() {
        val mime = MimeType.parse("Text/Plain; Charset=\"ISO-8859-1\"; lang=en")

        assertEquals("text/plain", mime.toString())
        assertEquals("ISO-8859-1", mime.charset)
        assertEquals("en", mime.parameters["lang"])
        assertEquals("utf-8", MimeType.parse("text/gemini").charset)
        assertFailsWith<GeminiException> { MimeType.parse("nonsense") }
    }
}
