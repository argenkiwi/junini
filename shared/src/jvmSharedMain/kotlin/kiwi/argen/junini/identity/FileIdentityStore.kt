package kiwi.argen.junini.identity

import java.io.File
import kiwi.argen.junini.writeTextAtomically
import kotlin.time.Instant

/**
 * Keeps identities in [dir]: an `identities` file with one `id fingerprint notAfterEpochSeconds name` line per
 * identity, an `assignments` file with one `host port id` line per assignment (malformed lines are skipped), and
 * for each identity `keys/<id>.crt` (the certificate chain) and `keys/<id>.key` (the private key), both PEM.
 *
 * The key isn't encrypted, so it is only as private as the app's own storage (Android's app-private `filesDir`,
 * the user's home directory on Desktop, where the file is also made readable by its owner alone where possible).
 */
class FileIdentityStore(private val dir: File) : IdentityStore {
    private val identitiesFile = File(dir, "identities")
    private val assignmentsFile = File(dir, "assignments")
    private val keysDir = File(dir, "keys")

    private var identities: MutableMap<String, Identity>? = null
    private var assigned: MutableMap<Pair<String, Int>, String>? = null

    @Synchronized
    override fun list(): List<Identity> = loadIdentities().values.toList()

    @Synchronized
    override fun get(id: String): Identity? = loadIdentities()[id]

    @Synchronized
    override fun add(identity: Identity, credentials: ClientCredentials) {
        // Keys first: an identity without a key would be listed but unusable.
        certificate(identity.id).writeTextAtomically(credentials.certificatePem)
        privateKey(identity.id).writeTextAtomically(credentials.privateKeyPem, ownerOnly = true)
        val identities = loadIdentities()
        identities[identity.id] = identity
        saveIdentities(identities)
    }

    @Synchronized
    override fun remove(id: String) {
        val identities = loadIdentities()
        if (identities.remove(id) == null) return
        saveIdentities(identities)
        val assigned = loadAssignments()
        if (assigned.values.removeAll { it == id }) saveAssignments(assigned)
        certificate(id).delete()
        privateKey(id).delete()
    }

    @Synchronized
    override fun assign(host: String, port: Int, id: String) {
        if (id !in loadIdentities()) return
        val assigned = loadAssignments()
        assigned[host.lowercase() to port] = id
        saveAssignments(assigned)
    }

    @Synchronized
    override fun unassign(host: String, port: Int) {
        val assigned = loadAssignments()
        if (assigned.remove(host.lowercase() to port) != null) saveAssignments(assigned)
    }

    @Synchronized
    override fun assignments(): Map<Pair<String, Int>, String> = loadAssignments().toMap()

    @Synchronized
    override fun credentials(id: String): ClientCredentials? {
        // Only ids from the index reach the filesystem, so a crafted id can't escape the keys directory.
        if (id !in loadIdentities()) return null
        val certificate = certificate(id)
        val privateKey = privateKey(id)
        if (!certificate.exists() || !privateKey.exists()) return null
        return ClientCredentials(certificate.readText(), privateKey.readText())
    }

    @Synchronized
    override fun credentialsFor(host: String, port: Int): ClientCredentials? =
        loadAssignments()[host.lowercase() to port]?.let(::credentials)

    private fun certificate(id: String) = File(keysDir, "$id.crt")

    private fun privateKey(id: String) = File(keysDir, "$id.key")

    private fun loadIdentities(): MutableMap<String, Identity> = identities ?: buildMap {
        if (identitiesFile.exists()) {
            identitiesFile.readLines().forEach { line ->
                val parts = line.trim().split(' ', limit = 4)
                if (parts.size != 4) return@forEach
                val notAfter = parts[2].toLongOrNull() ?: return@forEach
                put(parts[0], Identity(parts[0], parts[3], parts[1], Instant.fromEpochSeconds(notAfter)))
            }
        }
    }.toMutableMap().also { identities = it }

    private fun loadAssignments(): MutableMap<Pair<String, Int>, String> = assigned ?: buildMap {
        if (assignmentsFile.exists()) {
            assignmentsFile.readLines().forEach { line ->
                val parts = line.trim().split(' ')
                if (parts.size != 3) return@forEach
                val port = parts[1].toIntOrNull() ?: return@forEach
                put(parts[0] to port, parts[2])
            }
        }
    }.toMutableMap().also { assigned = it }

    private fun saveIdentities(identities: Map<String, Identity>) = identitiesFile.writeTextAtomically(
        identities.values.joinToString(separator = "") {
            "${it.id} ${it.fingerprint} ${it.notAfter.epochSeconds} ${it.name}\n"
        },
    )

    private fun saveAssignments(assigned: Map<Pair<String, Int>, String>) = assignmentsFile.writeTextAtomically(
        assigned.entries.joinToString(separator = "") { (key, id) -> "${key.first} ${key.second} $id\n" },
    )
}
