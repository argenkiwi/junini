package kiwi.argen.junini.gemini

import java.io.File
import kiwi.argen.junini.writeTextAtomically
import kotlin.time.Instant

/**
 * Keeps pinned certificates in a text file, one `host port fingerprint notAfterEpochSeconds` line
 * per entry. Fields are space-separated so IPv6 hosts don't need escaping. Malformed lines are skipped.
 */
class FileKnownHostsStore(private val file: File) : KnownHostsStore {
    private var entries: MutableMap<Pair<String, Int>, ServerCertificate>? = null

    @Synchronized
    override fun get(host: String, port: Int): ServerCertificate? = load()[host.lowercase() to port]

    @Synchronized
    override fun put(host: String, port: Int, certificate: ServerCertificate) {
        val entries = load()
        entries[host.lowercase() to port] = certificate
        save(entries)
    }

    private fun load(): MutableMap<Pair<String, Int>, ServerCertificate> = entries ?: buildMap {
        if (file.exists()) {
            file.readLines().forEach { line ->
                val parts = line.trim().split(' ')
                if (parts.size != 4) return@forEach
                val port = parts[1].toIntOrNull() ?: return@forEach
                val notAfter = parts[3].toLongOrNull() ?: return@forEach
                put(parts[0] to port, ServerCertificate(parts[2], Instant.fromEpochSeconds(notAfter)))
            }
        }
    }.toMutableMap().also { entries = it }

    private fun save(entries: Map<Pair<String, Int>, ServerCertificate>) = file.writeTextAtomically(
        entries.entries.joinToString(separator = "") { (key, cert) ->
            "${key.first} ${key.second} ${cert.fingerprint} ${cert.notAfter.epochSeconds}\n"
        },
    )
}
