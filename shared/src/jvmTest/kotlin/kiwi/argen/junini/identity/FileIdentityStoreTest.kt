package kiwi.argen.junini.identity

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class FileIdentityStoreTest {
    private val dir = Files.createTempDirectory("identities").toFile()
    private val identity = Identity("0123456789abcdef", "My identity", "0123456789abcdef".repeat(2), Instant.fromEpochSeconds(2_000_000_000))
    private val credentials = ClientCredentials("CERT-PEM\n", "KEY-PEM\n")

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun survivesANewInstance() {
        val store = FileIdentityStore(File(dir, "nested"))
        store.add(identity, credentials)
        store.assign("Example.org", 1965, identity.id)

        val reloaded = FileIdentityStore(File(dir, "nested"))
        assertEquals(listOf(identity), reloaded.list())
        assertEquals(mapOf(("example.org" to 1965) to identity.id), reloaded.assignments())
        val loaded = assertNotNull(reloaded.credentialsFor("example.org", 1965))
        assertEquals(credentials.certificatePem, loaded.certificatePem)
        assertEquals(credentials.privateKeyPem, loaded.privateKeyPem)
    }

    @Test
    fun removeDeletesTheKeyAndAssignments() {
        val store = FileIdentityStore(dir)
        store.add(identity, credentials)
        store.assign("example.org", 1965, identity.id)

        store.remove(identity.id)

        val reloaded = FileIdentityStore(dir)
        assertEquals(emptyList(), reloaded.list())
        assertEquals(emptyMap(), reloaded.assignments())
        assertNull(reloaded.credentials(identity.id))
        assertFalse(File(dir, "keys/${identity.id}.crt").exists())
        assertFalse(File(dir, "keys/${identity.id}.key").exists())
    }

    @Test
    fun storesPlainPemFilesAndKeepsTheKeyPrivateToTheOwner() {
        FileIdentityStore(dir).add(identity, credentials)

        assertEquals("CERT-PEM\n", File(dir, "keys/${identity.id}.crt").readText())
        val key = File(dir, "keys/${identity.id}.key")
        assertEquals("KEY-PEM\n", key.readText())
        // Skipped where the filesystem has no POSIX permissions.
        val permissions = runCatching { Files.getPosixFilePermissions(key.toPath()) }.getOrNull() ?: return
        assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), permissions)
    }

    @Test
    fun unassignForgetsTheHost() {
        val store = FileIdentityStore(dir)
        store.add(identity, credentials)
        store.assign("example.org", 1965, identity.id)
        store.unassign("example.org", 1965)

        assertNull(FileIdentityStore(dir).credentialsFor("example.org", 1965))
    }

    @Test
    fun namesMayContainSpaces() {
        FileIdentityStore(dir).add(identity.copy(name = "Name with  spaces"), credentials)

        assertEquals("Name with  spaces", FileIdentityStore(dir).get(identity.id)?.name)
    }

    @Test
    fun skipsMalformedLinesAndIgnoresPathsOutsideTheIndex() {
        File(dir, "identities").writeText("garbage\n${identity.id} ${identity.fingerprint} notanumber name\n")
        File(dir, "assignments").writeText("too few\nexample.org port id\n")
        val store = FileIdentityStore(dir)

        assertEquals(emptyList(), store.list())
        assertEquals(emptyMap(), store.assignments())
        assertNull(store.credentials("../../etc/passwd"))
    }

    @Test
    fun assigningAnUnknownIdentityDoesNothing() {
        val store = FileIdentityStore(dir)
        store.assign("example.org", 1965, "missing")

        assertEquals(emptyMap(), store.assignments())
    }
}
