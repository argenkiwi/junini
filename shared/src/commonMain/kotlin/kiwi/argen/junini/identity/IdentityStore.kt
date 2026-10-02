package kiwi.argen.junini.identity

import kotlin.time.Instant

/**
 * A client certificate the user can present to capsules. Only metadata lives here: the private key
 * stays in the store as [ClientCredentials].
 *
 * [id] is the start of the certificate's SHA-256 [fingerprint] (lowercase hex), so the same certificate can't be added twice.
 */
data class Identity(val id: String, val name: String, val fingerprint: String, val notAfter: Instant)

/**
 * What a TLS connection needs to present an identity, as PEM text: [certificatePem] holds the certificate chain
 * (the identity's own certificate first) and [privateKeyPem] its unencrypted private key, always PKCS#8.
 */
class ClientCredentials(val certificatePem: String, val privateKeyPem: String)

/** Identities and the hosts they are assigned to. A host:port has at most one identity. */
interface IdentityStore {
    fun list(): List<Identity>
    fun get(id: String): Identity?
    fun add(identity: Identity, credentials: ClientCredentials)

    /** Removes the identity, its key and every assignment that points at it. */
    fun remove(id: String)

    /** Assigns [id] to [host]:[port], replacing any identity assigned before. */
    fun assign(host: String, port: Int, id: String)
    fun unassign(host: String, port: Int)

    /** Host and port to identity id. */
    fun assignments(): Map<Pair<String, Int>, String>

    /** The key material of identity [id]. */
    fun credentials(id: String): ClientCredentials?

    /** The credentials to present to [host]:[port], or null if no identity is assigned to it. */
    fun credentialsFor(host: String, port: Int): ClientCredentials?
}

/** Forgets everything when the app closes. Used where there's no persistent store (and in tests). */
class InMemoryIdentityStore : IdentityStore {
    private val identities = linkedMapOf<String, Identity>()
    private val credentials = mutableMapOf<String, ClientCredentials>()
    private val assigned = mutableMapOf<Pair<String, Int>, String>()

    override fun list(): List<Identity> = identities.values.toList()

    override fun get(id: String): Identity? = identities[id]

    override fun add(identity: Identity, credentials: ClientCredentials) {
        identities[identity.id] = identity
        this.credentials[identity.id] = credentials
    }

    override fun remove(id: String) {
        identities.remove(id)
        credentials.remove(id)
        assigned.values.removeAll { it == id }
    }

    override fun assign(host: String, port: Int, id: String) {
        if (id in identities) assigned[host.lowercase() to port] = id
    }

    override fun unassign(host: String, port: Int) {
        assigned.remove(host.lowercase() to port)
    }

    override fun assignments(): Map<Pair<String, Int>, String> = assigned.toMap()

    override fun credentials(id: String): ClientCredentials? = credentials[id]

    override fun credentialsFor(host: String, port: Int): ClientCredentials? =
        assigned[host.lowercase() to port]?.let(credentials::get)
}
