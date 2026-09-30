package kiwi.argen.junini.ui

import io.ktor.http.Url
import kiwi.argen.junini.gemini.GeminiClient
import kiwi.argen.junini.gemini.GeminiTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
}
