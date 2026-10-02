package kiwi.argen.junini.history

import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class BrowsingHistoryTest {
    private var now = Instant.parse("2026-10-01T00:00:00Z")
    private val store = InMemoryHistoryStore()
    private val history = BrowsingHistory(store, now = { now }, maxEntries = 5)

    private fun visit(url: String, times: Int = 1) = repeat(times) { history.record(Url(url)) }

    private fun advance(by: Duration) {
        now += by
    }

    @Test
    fun repeatVisitsAreMergedUnderTheNormalisedUrl() {
        visit("gemini://Example.org")
        visit("gemini://example.org:1965/")
        visit("gemini://example.org/#top")

        assertEquals(listOf(Visit("gemini://example.org/", 3, now)), store.all())
    }

    @Test
    fun matchesPrefixWithOrWithoutScheme() {
        visit("gemini://example.org/a")
        visit("gemini://other.net/")

        assertEquals(listOf("gemini://example.org/a"), history.suggest("exa"))
        assertEquals(listOf("gemini://example.org/a"), history.suggest("gemini://exa"))
        assertEquals(listOf("gemini://example.org/a"), history.suggest("  EXA "))
    }

    @Test
    fun substringMatchesComeAfterPrefixMatches() {
        visit("gemini://blog.example.org/", times = 5)
        visit("gemini://example.org/")

        assertEquals(listOf("gemini://example.org/", "gemini://blog.example.org/"), history.suggest("example"))
    }

    @Test
    fun singleCharacterQueriesOnlyMatchPrefixes() {
        visit("gemini://example.org/")

        assertEquals(emptyList(), history.suggest("x"))
    }

    @Test
    fun ranksByVisitCountWeightedByRecency() {
        visit("gemini://example.org/old", times = 3)
        advance(60.days)
        visit("gemini://example.org/new", times = 2)
        visit("gemini://example.org/once")

        // old: 3 × 30 = 90, new: 2 × 100 = 200, once: 1 × 100 = 100.
        assertEquals(
            listOf("gemini://example.org/new", "gemini://example.org/once", "gemini://example.org/old"),
            history.suggest("example.org/"),
        )
    }

    @Test
    fun tiesGoToTheMostRecentVisit() {
        visit("gemini://example.org/a")
        advance(1.days)
        visit("gemini://example.org/b")

        assertEquals(listOf("gemini://example.org/b", "gemini://example.org/a"), history.suggest("example.org/"))
    }

    @Test
    fun capsTheNumberOfSuggestions() {
        repeat(5) { visit("gemini://example.org/$it") }

        assertEquals(3, history.suggest("example", limit = 3).size)
    }

    @Test
    fun evictsTheOldestEntriesBeyondTheCap() {
        repeat(6) {
            visit("gemini://example.org/$it")
            advance(1.days)
        }

        val urls = store.all().map { it.url }.toSet()
        assertEquals(5, urls.size)
        assertTrue("gemini://example.org/0" !in urls)
    }

    @Test
    fun revisitingAtTheCapDoesNotEvict() {
        repeat(5) { visit("gemini://example.org/$it") }
        visit("gemini://example.org/0")

        assertEquals(5, store.all().size)
    }

    @Test
    fun blankQueryAndExactOnlyMatchSuggestNothing() {
        visit("gemini://example.org/")

        assertEquals(emptyList(), history.suggest("  "))
        assertEquals(emptyList(), history.suggest("gemini://example.org/"))
        assertEquals(listOf("gemini://example.org/"), history.suggest("gemini://example.org"))
    }

    @Test
    fun clearForgetsEverything() {
        visit("gemini://example.org/")
        history.clear()

        assertEquals(emptyList(), store.all())
        assertEquals(emptyList(), history.suggest("example"))
    }
}
