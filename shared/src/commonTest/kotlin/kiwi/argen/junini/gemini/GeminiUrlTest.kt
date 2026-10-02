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

    @Test
    fun withQueryPercentEncodesInputWithSpacesAsPercent20() {
        assertEquals("gemini://example.org/docs/page.gmi?hello%20world", base.withQuery("hello world").toString())
        assertEquals("gemini://example.org/docs/page.gmi?a%3Db%26c", base.withQuery("a=b&c").toString())
    }

    @Test
    fun resolvingAPathDropsTheBaseQuery() {
        val withQuery = Url("gemini://example.org/ask?Ada%20Lovelace")
        assertEquals("gemini://example.org/ask", resolveUrl(withQuery, "/ask").toString())
        assertEquals("gemini://example.org/ask", resolveUrl(withQuery, "gemini://example.org/ask").toString())
        assertEquals("gemini://example.org/other", resolveUrl(withQuery, "other").toString())
    }

    @Test
    fun resolvingAQueryOnlyReferenceReplacesTheBaseQuery() {
        val withQuery = Url("gemini://example.org/ask?old")
        assertEquals("gemini://example.org/ask?new", resolveUrl(withQuery, "?new").toString())
    }

    @Test
    fun withQueryReplacesExistingQueryAndFragment() {
        val url = Url("gemini://example.org/a?old=1#top")
        assertEquals("gemini://example.org/a?new", url.withQuery("new").toString())
    }

    @Test
    fun withoutQueryDropsQueryAndFragment() {
        assertEquals("gemini://example.org/ask", Url("gemini://example.org/ask?secret#top").withoutQuery().toString())
    }
}
