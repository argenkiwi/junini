package kiwi.argen.junini.gemini

import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class GeminiUrlTest {
    private val base = Url("gemini://example.org/docs/page.gmi")

    @Test
    fun addsSchemeAndRootPath() {
        assertEquals("gemini://example.org/", parseUserInput("  example.org ").toString())
        assertEquals("gemini://example.org/", parseUserInput("gemini://example.org").toString())
        assertEquals("gemini://example.org/a?q=1", parseUserInput("example.org/a?q=1").toString())
    }

    @Test
    fun rejectsBlankAndOtherSchemes() {
        assertNull(parseUserInput(""))
        assertNull(parseUserInput("https://example.org/"))
    }

    @Test
    fun usesDefaultPortUnlessOneIsGiven() {
        assertEquals(GEMINI_DEFAULT_PORT, Url("gemini://example.org/").geminiPort)
        assertEquals(1966, Url("gemini://example.org:1966/").geminiPort)
    }

    @Test
    fun resolvesRelativeLinks() {
        assertEquals("gemini://example.org/docs/other.gmi", resolveUrl(base, "other.gmi").toString())
        assertEquals("gemini://example.org/root.gmi", resolveUrl(base, "/root.gmi").toString())
        assertEquals("gemini://example.org/up.gmi", resolveUrl(base, "../up.gmi").toString())
        assertEquals("gemini://example.org/", resolveUrl(base, "../../..").toString())
        assertEquals("gemini://example.org/docs/", resolveUrl(base, "./").toString())
        assertEquals("gemini://other.net/", resolveUrl(base, "//other.net/").toString())
        assertEquals("gemini://other.net/x", resolveUrl(base, "gemini://other.net/x").toString())
    }

    @Test
    fun normalizesForComparison() {
        assertEquals("gemini://example.org/", Url("gemini://Example.ORG:1965").normalized())
        assertEquals("gemini://example.org/", Url("gemini://example.org/#top").normalized())
        assertEquals("gemini://example.org:1966/a?q=1", Url("gemini://example.org:1966/a?q=1#x").normalized())
    }

    @Test
    fun resolvesOtherSchemesWithoutTouchingThem() {
        val url = resolveUrl(base, "https://example.com/page")!!
        assertFalse(url.isGemini)
        assertEquals("https://example.com/page", url.toString())
    }
}
