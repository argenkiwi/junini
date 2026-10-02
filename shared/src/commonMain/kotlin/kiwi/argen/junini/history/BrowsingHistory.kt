package kiwi.argen.junini.history

import io.ktor.http.Url
import kiwi.argen.junini.gemini.normalized
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** A page that loaded successfully: how often and when it was last visited. */
data class Visit(val url: String, val count: Int, val lastVisited: Instant)

/** Visits keyed by their normalised URL. It only stores them; the rules live in [BrowsingHistory]. */
interface HistoryStore {
    fun all(): List<Visit>
    fun put(visit: Visit)
    fun remove(url: String)
    fun clear()
}

/** Forgets everything when the app closes. Used where there's no persistent store (and in tests). */
class InMemoryHistoryStore : HistoryStore {
    private val entries = mutableMapOf<String, Visit>()

    override fun all(): List<Visit> = entries.values.toList()

    override fun put(visit: Visit) {
        entries[visit.url] = visit
    }

    override fun remove(url: String) {
        entries.remove(url)
    }

    override fun clear() = entries.clear()
}

/** Records visited pages and suggests them, ranked by frecency, as the user types a URL. */
class BrowsingHistory(
    private val store: HistoryStore,
    private val now: () -> Instant = { Clock.System.now() },
    private val maxEntries: Int = 500,
) {
    fun record(url: Url) {
        val key = url.normalized()
        val all = store.all()
        val count = all.find { it.url == key }?.count ?: 0
        store.put(Visit(key, count + 1, now()))
        val excess = all.size + (if (count == 0) 1 else 0) - maxEntries
        if (excess > 0) {
            all.filter { it.url != key }.sortedBy { it.lastVisited }.take(excess).forEach { store.remove(it.url) }
        }
    }

    /**
     * Up to [limit] visited URLs matching [query], with or without the `gemini://` scheme. URLs that
     * start with the query come first, then (for queries of two or more characters) those containing it.
     */
    fun suggest(query: String, limit: Int = 6): List<String> {
        val needle = query.trim().lowercase().removePrefix(SCHEME_PREFIX)
        if (needle.isEmpty()) return emptyList()
        val at = now()
        val matches = store.all().mapNotNull { visit ->
            val target = visit.url.lowercase().removePrefix(SCHEME_PREFIX)
            when {
                target.startsWith(needle) -> 0 to visit
                needle.length >= 2 && needle in target -> 1 to visit
                else -> null
            }
        }
        if (matches.size == 1 && matches.single().second.url.removePrefix(SCHEME_PREFIX) == needle) return emptyList()
        return matches
            .sortedWith(
                compareBy<Pair<Int, Visit>> { it.first }
                    .thenByDescending { frecency(it.second, at) }
                    .thenByDescending { it.second.lastVisited },
            )
            .take(limit)
            .map { it.second.url }
    }

    fun clear() = store.clear()

    private fun frecency(visit: Visit, at: Instant): Int {
        val age = at - visit.lastVisited
        val weight = when {
            age <= 4.days -> 100
            age <= 14.days -> 70
            age <= 31.days -> 50
            age <= 90.days -> 30
            else -> 10
        }
        return visit.count * weight
    }

    private companion object {
        const val SCHEME_PREFIX = "gemini://"
    }
}
