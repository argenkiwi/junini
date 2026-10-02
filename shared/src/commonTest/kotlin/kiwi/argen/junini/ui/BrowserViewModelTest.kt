package kiwi.argen.junini.ui

import io.ktor.http.Url
import kiwi.argen.junini.gemini.GeminiClient
import kiwi.argen.junini.gemini.GeminiTransport
import kiwi.argen.junini.gemini.InMemoryKnownHostsStore
import kiwi.argen.junini.gemini.KnownHosts
import kiwi.argen.junini.gemini.KnownHostsStore
import kiwi.argen.junini.gemini.ServerCertificate
import kiwi.argen.junini.history.HistoryStore
import kiwi.argen.junini.history.InMemoryHistoryStore
import kiwi.argen.junini.identity.FakeIdentityCodec
import kiwi.argen.junini.identity.IdentityManager
import kiwi.argen.junini.identity.IdentityStore
import kiwi.argen.junini.identity.InMemoryIdentityStore
import kiwi.argen.junini.identity.fakeCertificate
import kiwi.argen.junini.identity.fakeKey
import kiwi.argen.junini.identity.importFake
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelTest {

    private class FakeTransport(private val responses: Map<String, String>) : GeminiTransport {
        override suspend fun fetch(host: String, port: Int, request: String): ByteArray {
            val url = request.removeSuffix("\r\n")
            return (responses[url] ?: "51 Not found\r\n").encodeToByteArray()
        }
    }

    private val sampleResponses = mapOf(
        "gemini://example.org/a" to "20 text/gemini\r\n# Page A",
        "gemini://example.org/b" to "20 text/gemini\r\n# Page B",
        "gemini://example.org/c" to "20 text/gemini\r\n# Page C",
        "gemini://example.org/slow" to "20 text/gemini\r\n# Slow",
        "gemini://example.org/err" to "51 Not found\r\n",
    )

    private fun createViewModel(responses: Map<String, String> = sampleResponses, maxHistory: Int = 50): BrowserViewModel {
        val client = GeminiClient(FakeTransport(responses))
        return BrowserViewModel(client = client, maxHistorySize = maxHistory)
    }

    @Test
    fun initialStateHasNoNavigationHistory() {
        val vm = createViewModel()
        assertFalse(vm.state.value.canGoBack)
        assertFalse(vm.state.value.canGoForward)
        assertEquals(PageState.Idle, vm.state.value.page)
    }

    @Test
    fun firstLoadedPageCannotGoBackOrForward() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val vm = createViewModel()
            vm.onUrlInputChange("gemini://example.org/a")
            vm.submitUrlInput()
            advanceUntilIdle()

            assertEquals(Url("gemini://example.org/a"), vm.state.value.currentUrl)
            assertIs<PageState.Gemtext>(vm.state.value.page)
            assertFalse(vm.state.value.canGoBack)
            assertFalse(vm.state.value.canGoForward)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun navigatingToSecondPageEnablesBackNavigation() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val vm = createViewModel()
            vm.onUrlInputChange("gemini://example.org/a")
            vm.submitUrlInput()
            advanceUntilIdle()

            vm.onUrlInputChange("gemini://example.org/b")
            vm.submitUrlInput()
            advanceUntilIdle()

            assertEquals(Url("gemini://example.org/b"), vm.state.value.currentUrl)
            assertTrue(vm.state.value.canGoBack)
            assertFalse(vm.state.value.canGoForward)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun goBackRestoresPreviousPageAndEnablesForward() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val vm = createViewModel()
            vm.onUrlInputChange("gemini://example.org/a")
            vm.submitUrlInput()
            advanceUntilIdle()

            vm.onUrlInputChange("gemini://example.org/b")
            vm.submitUrlInput()
            advanceUntilIdle()

            vm.goBack()

            assertEquals(Url("gemini://example.org/a"), vm.state.value.currentUrl)
            assertEquals("gemini://example.org/a", vm.state.value.urlInput)
            val page = assertIs<PageState.Gemtext>(vm.state.value.page)
            assertEquals("Page A", page.lines.first().let { (it as kiwi.argen.junini.gemini.GemtextLine.Heading).text })
            assertFalse(vm.state.value.canGoBack)
            assertTrue(vm.state.value.canGoForward)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun goForwardRestoresForwardPageAndEnablesBack() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val vm = createViewModel()
            vm.onUrlInputChange("gemini://example.org/a")
            vm.submitUrlInput()
            advanceUntilIdle()

            vm.onUrlInputChange("gemini://example.org/b")
            vm.submitUrlInput()
            advanceUntilIdle()

            vm.goBack()
            vm.goForward()

            assertEquals(Url("gemini://example.org/b"), vm.state.value.currentUrl)
            assertEquals("gemini://example.org/b", vm.state.value.urlInput)
            val page = assertIs<PageState.Gemtext>(vm.state.value.page)
            assertEquals("Page B", page.lines.first().let { (it as kiwi.argen.junini.gemini.GemtextLine.Heading).text })
            assertTrue(vm.state.value.canGoBack)
            assertFalse(vm.state.value.canGoForward)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun newNavigationTruncatesForwardStack() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val vm = createViewModel()
            vm.onUrlInputChange("gemini://example.org/a")
            vm.submitUrlInput()
            advanceUntilIdle()

            vm.onUrlInputChange("gemini://example.org/b")
            vm.submitUrlInput()
            advanceUntilIdle()

            vm.goBack()
            assertTrue(vm.state.value.canGoForward)

            // Navigate to C from A
            vm.onUrlInputChange("gemini://example.org/c")
            vm.submitUrlInput()
            advanceUntilIdle()

            assertEquals(Url("gemini://example.org/c"), vm.state.value.currentUrl)
            assertTrue(vm.state.value.canGoBack)
            assertFalse(vm.state.value.canGoForward) // B is discarded
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun reloadingSameUrlDoesNotDuplicateBackStack() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val vm = createViewModel()
            vm.onUrlInputChange("gemini://example.org/a")
            vm.submitUrlInput()
            advanceUntilIdle()

            vm.onUrlInputChange("gemini://example.org/b")
            vm.submitUrlInput()
            advanceUntilIdle()

            // Reload B by submitting same URL
            vm.onUrlInputChange("gemini://example.org/b")
            vm.submitUrlInput()
            advanceUntilIdle()

            vm.goBack()
            // Back should take us directly to A, not the duplicate B
            assertEquals(Url("gemini://example.org/a"), vm.state.value.currentUrl)
            assertFalse(vm.state.value.canGoBack)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun errorPagePreservesBackNavigationToPreviousPage() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val vm = createViewModel()
            vm.onUrlInputChange("gemini://example.org/a")
            vm.submitUrlInput()
            advanceUntilIdle()

            vm.onUrlInputChange("gemini://example.org/err")
            vm.submitUrlInput()
            advanceUntilIdle()

            assertIs<PageState.Message>(vm.state.value.page)
            assertTrue(vm.state.value.canGoBack)

            vm.goBack()
            assertEquals(Url("gemini://example.org/a"), vm.state.value.currentUrl)
            assertIs<PageState.Gemtext>(vm.state.value.page)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun goBackCancelsInFlightLoad() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val vm = createViewModel()
            vm.onUrlInputChange("gemini://example.org/a")
            vm.submitUrlInput()
            advanceUntilIdle()

            vm.onUrlInputChange("gemini://example.org/b")
            vm.submitUrlInput()
            // Do not advance test scheduler so b is in-flight
            assertTrue(vm.state.value.isLoading)

            vm.goBack()
            advanceUntilIdle()

            assertEquals(Url("gemini://example.org/a"), vm.state.value.currentUrl)
            assertFalse(vm.state.value.isLoading)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /** Serves every request from a host, but checks its certificate through [knownHosts] first, like the real transport. */
    private class PinningTransport(
        private val knownHosts: KnownHosts,
        var certificate: ServerCertificate,
    ) : GeminiTransport {
        override suspend fun fetch(host: String, port: Int, request: String): ByteArray {
            knownHosts.verify(host, port, certificate)
            return "20 text/gemini\r\n# ${request.trim()}".encodeToByteArray()
        }
    }

    private val original = ServerCertificate("aa11", Instant.parse("2099-01-01T00:00:00Z"))
    private val replacement = ServerCertificate("bb22", Instant.parse("2099-01-01T00:00:00Z"))

    private fun certificateViewModel(): Triple<BrowserViewModel, PinningTransport, KnownHostsStore> {
        val store = InMemoryKnownHostsStore()
        val knownHosts = KnownHosts(store)
        val transport = PinningTransport(knownHosts, original)
        return Triple(BrowserViewModel(store, knownHosts, client = GeminiClient(transport)), transport, store)
    }

    private fun BrowserViewModel.open(url: String) {
        onUrlInputChange(url)
        submitUrlInput()
    }

    @Test
    fun certificateMismatchShowsPrompt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val (vm, transport) = certificateViewModel()
            vm.open("gemini://example.org/a")
            advanceUntilIdle()

            transport.certificate = replacement
            vm.open("gemini://example.org/b")
            advanceUntilIdle()

            val page = assertIs<PageState.CertificateChanged>(vm.state.value.page)
            assertEquals("example.org", page.host)
            assertEquals(original, page.pinned)
            assertEquals(replacement, page.presented)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun trustingNewCertificatePinsItAndReloads() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val (vm, transport, store) = certificateViewModel()
            vm.open("gemini://example.org/a")
            advanceUntilIdle()
            transport.certificate = replacement
            vm.open("gemini://example.org/b")
            advanceUntilIdle()

            vm.trustNewCertificate()
            advanceUntilIdle()

            assertEquals(replacement, store.get("example.org", 1965))
            assertEquals(Url("gemini://example.org/b"), vm.state.value.currentUrl)
            assertIs<PageState.Gemtext>(vm.state.value.page)
            // The prompt isn't kept in history: back goes straight to A.
            vm.goBack()
            assertEquals(Url("gemini://example.org/a"), vm.state.value.currentUrl)
            assertFalse(vm.state.value.canGoBack)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun cancellingCertificateChangeGoesBackAndKeepsPin() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val (vm, transport, store) = certificateViewModel()
            vm.open("gemini://example.org/a")
            advanceUntilIdle()
            transport.certificate = replacement
            vm.open("gemini://example.org/b")
            advanceUntilIdle()

            vm.cancelCertificateChange()

            assertEquals(original, store.get("example.org", 1965))
            assertEquals(Url("gemini://example.org/a"), vm.state.value.currentUrl)
            assertIs<PageState.Gemtext>(vm.state.value.page)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private val historyResponses = mapOf(
        "gemini://example.org/a" to "20 text/gemini\r\n# Page A",
        "gemini://example.org/text" to "20 text/plain\r\nHello",
        "gemini://example.org/moved" to "31 /a\r\n",
        "gemini://example.org/away" to "30 https://example.com/\r\n",
        "gemini://example.org/input" to "10 Your name?\r\n",
        "gemini://example.org/image" to "20 image/png\r\n\u0089PNG",
        "gemini://example.org/broken" to "garbage without a header line",
    )

    private fun historyViewModel(store: HistoryStore = InMemoryHistoryStore()) =
        BrowserViewModel(client = GeminiClient(FakeTransport(historyResponses)), historyStore = store)

    private fun TestScope.recordedAfterOpening(vararg urls: String): Set<String> {
        val store = InMemoryHistoryStore()
        val vm = historyViewModel(store)
        urls.forEach {
            vm.open(it)
            advanceUntilIdle()
        }
        return store.all().map { it.url }.toSet()
    }

    @Test
    fun recordsPagesThatRendered() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            assertEquals(
                setOf("gemini://example.org/a", "gemini://example.org/text"),
                recordedAfterOpening("gemini://example.org/a", "example.org/text"),
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun redirectsRecordTheFinalUrl() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            assertEquals(setOf("gemini://example.org/a"), recordedAfterOpening("gemini://example.org/moved"))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun doesNotRecordPagesThatDidNotRender() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            assertEquals(
                emptySet(),
                recordedAfterOpening(
                    "gemini://example.org/missing",
                    "gemini://example.org/away",
                    "gemini://example.org/input",
                    "gemini://example.org/image",
                    "gemini://example.org/broken",
                    "https://example.org/",
                    "",
                ),
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun typingSuggestsVisitedPagesUntilTheNextLoad() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = historyViewModel()
            vm.open("gemini://example.org/a")
            advanceUntilIdle()

            vm.onUrlInputChange("exa")
            assertEquals(listOf("gemini://example.org/a"), vm.state.value.suggestions)

            vm.pickSuggestion("gemini://example.org/a")
            assertEquals(emptyList(), vm.state.value.suggestions)
            advanceUntilIdle()
            assertEquals(Url("gemini://example.org/a"), vm.state.value.currentUrl)
            assertIs<PageState.Gemtext>(vm.state.value.page)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun dismissingHidesSuggestions() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = historyViewModel()
            vm.open("gemini://example.org/a")
            advanceUntilIdle()
            vm.onUrlInputChange("exa")

            vm.dismissSuggestions()

            assertEquals(emptyList(), vm.state.value.suggestions)
            assertEquals("exa", vm.state.value.urlInput)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun clearingHistoryRemovesSuggestions() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = InMemoryHistoryStore()
            val vm = historyViewModel(store)
            vm.open("gemini://example.org/a")
            advanceUntilIdle()
            vm.onUrlInputChange("exa")

            vm.clearHistory()

            assertEquals(emptyList(), store.all())
            assertEquals(emptyList(), vm.state.value.suggestions)
            assertEquals("History cleared", vm.state.value.notice)
            vm.onUrlInputChange("exam")
            assertEquals(emptyList(), vm.state.value.suggestions)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun cancellingCertificateChangeWithNoHistoryReturnsToIdle() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val (vm, transport, store) = certificateViewModel()
            store.put("example.org", 1965, original)
            transport.certificate = replacement
            vm.open("gemini://example.org/a")
            advanceUntilIdle()

            vm.cancelCertificateChange()

            assertEquals(PageState.Idle, vm.state.value.page)
            assertEquals(null, vm.state.value.currentUrl)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /** Answers 60 until an identity is assigned to the host, like a capsule that requires a login. */
    private class IdentityRequiredTransport(private val identities: InMemoryIdentityStore) : GeminiTransport {
        val presented = mutableListOf<String?>()

        override suspend fun fetch(host: String, port: Int, request: String): ByteArray {
            val credentials = identities.credentialsFor(host, port)
            presented += credentials?.certificatePem
            return if (credentials == null) "60 Please identify yourself\r\n".encodeToByteArray()
            else "20 text/gemini\r\n# Hello".encodeToByteArray()
        }
    }

    private val fingerprintA = "aa".repeat(32)

    private fun identityViewModel(): Triple<BrowserViewModel, IdentityManager, IdentityRequiredTransport> {
        val identities = InMemoryIdentityStore()
        val manager = IdentityManager(identities, FakeIdentityCodec())
        val transport = IdentityRequiredTransport(identities)
        return Triple(BrowserViewModel(identityManager = manager, client = GeminiClient(transport)), manager, transport)
    }

    @Test
    fun status60ShowsTheIdentityPrompt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val (vm, manager, _) = identityViewModel()
            val existing = manager.importFake(fingerprintA, "Existing")
            vm.open("gemini://example.org/")
            advanceUntilIdle()

            val page = assertIs<PageState.ClientCertificateRequired>(vm.state.value.page)
            assertEquals("example.org", page.host)
            assertEquals(1965, page.port)
            assertEquals(60, page.status)
            assertNull(page.current)
            assertEquals(listOf(existing), page.available)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun pickingAnIdentityAssignsItAndReloadsThePage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val (vm, manager, transport) = identityViewModel()
            val identity = manager.importFake(fingerprintA, "Existing")
            vm.open("gemini://example.org/")
            advanceUntilIdle()

            vm.useIdentity(identity.id)
            advanceUntilIdle()

            assertIs<PageState.Gemtext>(vm.state.value.page)
            assertEquals(identity, manager.assignedTo("example.org", 1965))
            assertNotNull(transport.presented.last())
            // The prompt isn't a page to go back to.
            assertFalse(vm.state.value.canGoBack)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun importingFromThePromptAssignsTheIdentityAndReloads() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val (vm, manager, _) = identityViewModel()
            vm.open("gemini://example.org/")
            advanceUntilIdle()

            vm.importIdentityForPage(listOf(fakeCertificate(fingerprintA, "Imported"), fakeKey()))
            advanceUntilIdle()

            assertIs<PageState.Gemtext>(vm.state.value.page)
            assertEquals("Imported", manager.assignedTo("example.org", 1965)?.name)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun aFailedImportFromThePromptKeepsThePromptAndExplainsWhy() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val (vm, manager, _) = identityViewModel()
            vm.open("gemini://example.org/")
            advanceUntilIdle()

            vm.importIdentityForPage(listOf(fakeCertificate(fingerprintA)))
            advanceUntilIdle()

            assertIs<PageState.ClientCertificateRequired>(vm.state.value.page)
            assertEquals("No private key found", vm.state.value.notice)
            assertEquals(emptyList(), manager.identities())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun unassigningFromThePromptShowsItAgainWithoutACurrentIdentity() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val (vm, manager, _) = identityViewModel()
            val identity = manager.importFake(fingerprintA, "Existing")
            manager.assign("example.org", 1965, identity.id)
            vm.open("gemini://example.org/a")
            advanceUntilIdle()
            assertIs<PageState.Gemtext>(vm.state.value.page)

            manager.unassign("example.org", 1965)
            vm.open("gemini://example.org/b")
            advanceUntilIdle()
            manager.assign("example.org", 1965, identity.id)
            vm.unassignIdentityForPage()
            advanceUntilIdle()

            val page = assertIs<PageState.ClientCertificateRequired>(vm.state.value.page)
            assertNull(page.current)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun withoutAnIdentityManagerStatus60IsAPlainError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = createViewModel(mapOf("gemini://example.org/" to "60 Certificate required\r\n"))
            vm.open("gemini://example.org/")
            advanceUntilIdle()

            assertIs<PageState.Message>(vm.state.value.page)
            assertFalse(vm.state.value.identitiesAvailable)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun importingFromTheIdentityScreenRefreshesTheList() {
        val (vm, manager, _) = identityViewModel()
        vm.openIdentities()

        vm.importIdentity(listOf(fakeCertificate(fingerprintA, "New one"), fakeKey()))

        assertEquals(listOf("New one"), vm.state.value.identities?.map { it.identity.name })
        assertEquals("Identity imported", vm.state.value.notice)
        assertEquals(1, manager.identities().size)
    }

    @Test
    fun exportGivesPemTextAndAnUnknownIdentityGivesANotice() {
        val (vm, manager, _) = identityViewModel()
        val identity = manager.importFake(fingerprintA)

        assertTrue(vm.exportIdentity(identity.id)!!.contains("-----BEGIN PRIVATE KEY-----"))
        assertNull(vm.exportIdentity("missing"))
        assertEquals("Identity not found", vm.state.value.notice)
    }

    @Test
    fun identityScreenListsIdentitiesWithTheirHosts() {
        val (vm, manager, _) = identityViewModel()
        val identity = manager.importFake(fingerprintA, "Listed")
        manager.assign("a.example", 1965, identity.id)
        manager.assign("b.example", 1966, identity.id)

        vm.openIdentities()

        assertEquals(
            listOf(IdentityItem(identity, listOf(IdentityHost("a.example", 1965), IdentityHost("b.example", 1966)))),
            vm.state.value.identities,
        )
        vm.closeIdentities()
        assertNull(vm.state.value.identities)
    }

    @Test
    fun assigningFromTheIdentityScreenAcceptsHostsWithPorts() {
        val (vm, manager, _) = identityViewModel()
        val identity = manager.importFake(fingerprintA, "Listed")
        vm.openIdentities()

        vm.assignIdentity("Example.org:1966", identity.id)

        assertEquals(identity, manager.assignedTo("example.org", 1966))
        assertEquals(listOf(IdentityHost("example.org", 1966)), vm.state.value.identities?.single()?.hosts)

        vm.unassignIdentity("example.org", 1966)
        assertEquals(emptyList(), vm.state.value.identities?.single()?.hosts)
    }

    @Test
    fun ipv6HostsWithAPortCanBeUnassigned() {
        val (vm, manager, _) = identityViewModel()
        val identity = manager.importFake(fingerprintA)
        manager.assign("::1", 1966, identity.id)
        vm.openIdentities()

        val capsule = vm.state.value.identities!!.single().hosts.single()
        assertEquals(IdentityHost("::1", 1966), capsule)
        assertEquals("[::1]:1966", capsule.label)
        assertEquals("example.org", IdentityHost("example.org", 1965).label)
        assertEquals("example.org:1966", IdentityHost("example.org", 1966).label)

        vm.unassignIdentity(capsule.host, capsule.port)
        assertEquals(emptyList(), vm.state.value.identities?.single()?.hosts)
        assertNull(manager.assignedTo("::1", 1966))
    }

    /** A store whose changes to assignments fail, to check that the page isn't reloaded after a failure. */
    private class FailingAssignStore(private val delegate: InMemoryIdentityStore) : IdentityStore by delegate {
        override fun assign(host: String, port: Int, id: String) = throw IllegalStateException("Disk full")
        override fun unassign(host: String, port: Int) = throw IllegalStateException("Disk full")
    }

    @Test
    fun aFailedAssignOrUnassignFromThePromptDoesNotReload() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val identities = InMemoryIdentityStore()
            val manager = IdentityManager(FailingAssignStore(identities), FakeIdentityCodec())
            val transport = IdentityRequiredTransport(identities)
            val vm = BrowserViewModel(identityManager = manager, client = GeminiClient(transport))
            val identity = manager.importFake(fingerprintA)
            vm.open("gemini://example.org/")
            advanceUntilIdle()
            val requests = transport.presented.size

            vm.useIdentity(identity.id)
            advanceUntilIdle()
            assertEquals("Disk full", vm.state.value.notice)
            vm.unassignIdentityForPage()
            advanceUntilIdle()

            assertEquals(requests, transport.presented.size)
            assertIs<PageState.ClientCertificateRequired>(vm.state.value.page)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun deletingFromTheIdentityScreenRefreshesTheList() {
        val (vm, manager, _) = identityViewModel()
        val identity = manager.importFake(fingerprintA)
        vm.openIdentities()

        vm.deleteIdentity(identity.id)

        assertEquals(emptyList(), vm.state.value.identities)
    }
}
