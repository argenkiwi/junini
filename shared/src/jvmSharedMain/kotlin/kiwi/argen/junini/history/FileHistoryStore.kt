package kiwi.argen.junini.history

import java.io.File
import kiwi.argen.junini.writeTextAtomically
import kotlin.time.Instant

/**
 * Keeps visits in a text file, one `count lastVisitedEpochSeconds url` line per entry. The URL comes
 * last so it never needs escaping. Malformed lines are skipped.
 */
class FileHistoryStore(private val file: File) : HistoryStore {
    private var entries: MutableMap<String, Visit>? = null

    @Synchronized
    override fun all(): List<Visit> = load().values.toList()

    @Synchronized
    override fun put(visit: Visit) {
        val entries = load()
        entries[visit.url] = visit
        save(entries)
    }

    @Synchronized
    override fun remove(url: String) {
        val entries = load()
        if (entries.remove(url) != null) save(entries)
    }

    @Synchronized
    override fun clear() {
        load().clear()
        file.delete()
    }

    private fun load(): MutableMap<String, Visit> = entries ?: buildMap {
        if (file.exists()) {
            file.readLines().forEach { line ->
                val parts = line.trim().split(' ', limit = 3)
                if (parts.size != 3 || parts[2].isEmpty()) return@forEach
                val count = parts[0].toIntOrNull() ?: return@forEach
                val lastVisited = parts[1].toLongOrNull() ?: return@forEach
                put(parts[2], Visit(parts[2], count, Instant.fromEpochSeconds(lastVisited)))
            }
        }
    }.toMutableMap().also { entries = it }

    private fun save(entries: Map<String, Visit>) = file.writeTextAtomically(
        entries.values.joinToString(separator = "") { "${it.count} ${it.lastVisited.epochSeconds} ${it.url}\n" },
    )
}
