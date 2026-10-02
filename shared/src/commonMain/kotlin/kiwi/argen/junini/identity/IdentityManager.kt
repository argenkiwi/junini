package kiwi.argen.junini.identity

import kotlin.time.Instant

/** An identity read from PEM files, ready to be stored. */
class ParsedIdentity(val credentials: ClientCredentials, val fingerprint: String, val notAfter: Instant, val commonName: String?)

/** The platform's X.509 handling, kept out of common code. */
interface IdentityCodec {
    /**
     * Reads one identity from the text of one or more PEM files, in any order.
     *
     * @throws InvalidIdentityException with a message for the user if it can't be used: no certificate or no key,
     * a password-protected or non-RSA key, or a key that doesn't belong to the certificate.
     */
    fun parse(texts: List<String>): ParsedIdentity
}

class InvalidIdentityException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The rules for importing, exporting and assigning identities. */
class IdentityManager(
    val store: IdentityStore,
    private val codec: IdentityCodec,
) {
    fun identities(): List<Identity> = store.list()

    fun assignments(): Map<Pair<String, Int>, String> = store.assignments()

    /** The identity assigned to [host]:[port], if any. */
    fun assignedTo(host: String, port: Int): Identity? =
        store.assignments()[host.lowercase() to port]?.let(store::get)

    /**
     * Adds the identity in [texts], the contents of one PEM file holding a certificate and its private key or of
     * a certificate file and a key file. It is named after the certificate's common name.
     *
     * @throws InvalidIdentityException if the files can't be used or the identity has already been added.
     */
    fun import(texts: List<String>): Identity {
        val parsed = codec.parse(texts)
        val id = parsed.fingerprint.take(ID_LENGTH)
        if (store.get(id) != null) throw InvalidIdentityException("This identity has already been added")
        val name = parsed.commonName?.trim()?.replace(Regex("\\s+"), " ").orEmpty().ifEmpty { DEFAULT_NAME }
        val identity = Identity(id, name, parsed.fingerprint, parsed.notAfter)
        store.add(identity, parsed.credentials)
        return identity
    }

    /** The identity as one PEM file with its certificate and private key. It contains the private key. */
    fun export(id: String): String {
        val credentials = store.credentials(id) ?: throw InvalidIdentityException("Identity not found")
        return credentials.certificatePem.trimEnd() + "\n" + credentials.privateKeyPem.trimEnd() + "\n"
    }

    fun delete(id: String) = store.remove(id)

    fun assign(host: String, port: Int, id: String) = store.assign(host, port, id)

    fun unassign(host: String, port: Int) = store.unassign(host, port)

    private companion object {
        const val ID_LENGTH = 16
        const val DEFAULT_NAME = "Imported identity"
    }
}
