package kiwi.argen.junini.identity

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class IdentityManagerTest {
    private val store = InMemoryIdentityStore()
    private val manager = IdentityManager(store, FakeIdentityCodec())
    private val fingerprintA = "aa".repeat(32)
    private val fingerprintB = "bb".repeat(32)

    @Test
    fun importedIdentityIsNamedAfterTheCertificateAndStored() {
        val notAfter = Instant.parse("2040-05-06T07:08:09Z")
        val identity = manager.import(listOf(fakeCertificate(fingerprintA, "  My   identity ", notAfter), fakeKey()))

        assertEquals(Identity(fingerprintA.take(16), "My identity", fingerprintA, notAfter), identity)
        assertEquals(listOf(identity), manager.identities())
        assertNotNull(store.credentials(identity.id))
    }

    @Test
    fun certificateWithoutACommonNameGetsADefaultName() {
        assertEquals("Imported identity", manager.import(listOf(fakeCertificate(fingerprintA, name = "") + fakeKey())).name)
    }

    @Test
    fun certificateAndKeyCanComeFromTwoFilesInEitherOrder() {
        manager.import(listOf(fakeCertificate(fingerprintA), fakeKey()))
        manager.import(listOf(fakeKey(), fakeCertificate(fingerprintB)))

        assertEquals(2, manager.identities().size)
    }

    @Test
    fun invalidFilesAreRejectedWithoutStoringAnything() {
        assertFailsWith<InvalidIdentityException> { manager.import(listOf(fakeCertificate(fingerprintA))) }
        assertFailsWith<InvalidIdentityException> { manager.import(listOf(fakeKey())) }
        assertFailsWith<InvalidIdentityException> { manager.import(listOf("not a pem file")) }
        assertEquals(emptyList(), manager.identities())
    }

    @Test
    fun importingTheSameIdentityTwiceFails() {
        manager.importFake(fingerprintA)

        assertFailsWith<InvalidIdentityException> { manager.importFake(fingerprintA, "Again") }
        assertEquals(1, manager.identities().size)
    }

    @Test
    fun exportIsOnePemFileWithTheCertificateThenTheKeyAndImportsAgain() {
        val identity = manager.importFake(fingerprintA)
        val exported = manager.export(identity.id)

        assertEquals(listOf("CERTIFICATE", "PRIVATE KEY"), parsePemBlocks(exported).map { it.label })
        assertContains(exported, "\n-----BEGIN PRIVATE KEY-----")

        manager.delete(identity.id)
        assertEquals(identity, manager.import(listOf(exported)))
    }

    @Test
    fun exportingAnUnknownIdentityFails() {
        assertFailsWith<InvalidIdentityException> { manager.export("nope") }
    }

    @Test
    fun assigningReplacesThePreviousIdentityAndUnassigningRemovesIt() {
        val first = manager.importFake(fingerprintA, "First")
        val second = manager.importFake(fingerprintB, "Second")

        manager.assign("Example.org", 1965, first.id)
        assertEquals(first, manager.assignedTo("example.org", 1965))
        assertNotNull(store.credentialsFor("example.org", 1965))

        manager.assign("example.org", 1965, second.id)
        assertEquals(second, manager.assignedTo("example.org", 1965))
        assertNull(manager.assignedTo("example.org", 1966))

        manager.unassign("example.org", 1965)
        assertNull(manager.assignedTo("example.org", 1965))
        assertNull(store.credentialsFor("example.org", 1965))
    }

    @Test
    fun anIdentityCanServeSeveralHosts() {
        val identity = manager.importFake(fingerprintA)
        manager.assign("a.example", 1965, identity.id)
        manager.assign("b.example", 1965, identity.id)

        assertEquals(identity, manager.assignedTo("a.example", 1965))
        assertEquals(identity, manager.assignedTo("b.example", 1965))
    }

    @Test
    fun deletingAnIdentityRemovesItsAssignmentsAndKey() {
        val keep = manager.importFake(fingerprintA, "Keep")
        val drop = manager.importFake(fingerprintB, "Drop")
        manager.assign("a.example", 1965, drop.id)
        manager.assign("b.example", 1965, keep.id)

        manager.delete(drop.id)

        assertEquals(listOf(keep), manager.identities())
        assertNull(manager.assignedTo("a.example", 1965))
        assertNull(store.credentials(drop.id))
        assertEquals(keep, manager.assignedTo("b.example", 1965))
    }

    @Test
    fun assigningAnUnknownIdentityDoesNothing() {
        manager.assign("a.example", 1965, "missing")
        assertEquals(emptyMap(), manager.assignments())
    }
}
