package kiwi.argen.junini.gemini

import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class GeminiClientTest {

    private class FakeTransport(private val responses: Map<String, String>) : GeminiTransport {
        val requests = mutableListOf<Triple<String, Int, String>>()

        override suspend fun fetch(host: String, port: Int, request: String): ByteArray {
            requests += Triple(host, port, request)
            val url = request.removeSuffix("\r\n")
            return (responses[url] ?: "51 Not found\r\n").encodeToByteArray()
        }
    }

    @Test
    fun fetchesAndDecodesGemtext() = runTest {
        val transport = FakeTransport(mapOf("gemini://example.org/" to "20 text/gemini\r\n# Héllo"))

        val response = GeminiClient(transport).fetch(Url("gemini://example.org/"))

        val success = assertIs<GeminiResponse.Success>(response)
        assertEquals("# Héllo", success.decodeText())
        assertEquals(listOf(Triple("example.org", 1965, "gemini://example.org/\r\n")), transport.requests)
    }

    @Test
    fun followsRelativeRedirects() = runTest {
        val transport = FakeTransport(
            mapOf(
                "gemini://example.org/old" to "31 /new\r\n",
                "gemini://example.org/new" to "20 text/gemini\r\nmoved",
            ),
        )

        val response = GeminiClient(transport).fetch(Url("gemini://example.org/old"))

        assertEquals("gemini://example.org/new", response.url.toString())
    }

    @Test
    fun stopsAtRedirectLimit() = runTest {
        val responses = (0..10).associate { "gemini://example.org/$it" to "30 /${it + 1}\r\n" }

        assertFailsWith<GeminiException> {
            GeminiClient(FakeTransport(responses), maxRedirects = 5).fetch(Url("gemini://example.org/0"))
        }
    }

    @Test
    fun detectsRedirectLoops() = runTest {
        val transport = FakeTransport(
            mapOf(
                "gemini://example.org/a" to "30 /b\r\n",
                "gemini://example.org/b" to "30 /a\r\n",
            ),
        )

        assertFailsWith<GeminiException> { GeminiClient(transport).fetch(Url("gemini://example.org/a")) }
    }

    @Test
    fun returnsCrossSchemeRedirectToCaller() = runTest {
        val transport = FakeTransport(mapOf("gemini://example.org/" to "30 https://example.org/\r\n"))

        val response = GeminiClient(transport).fetch(Url("gemini://example.org/"))

        assertEquals("https://example.org/", assertIs<GeminiResponse.Redirect>(response).target)
    }

    @Test
    fun rejectsUnsupportedCharsets() = runTest {
        val transport = FakeTransport(mapOf("gemini://example.org/" to "20 text/plain; charset=iso-8859-1\r\nx"))

        val response = assertIs<GeminiResponse.Success>(GeminiClient(transport).fetch(Url("gemini://example.org/")))

        assertFailsWith<GeminiException> { response.decodeText() }
    }
}
