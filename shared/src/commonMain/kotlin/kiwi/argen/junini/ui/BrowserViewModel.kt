package kiwi.argen.junini.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.http.Url
import kiwi.argen.junini.gemini.CertificateMismatchException
import kiwi.argen.junini.gemini.GeminiClient
import kiwi.argen.junini.gemini.GeminiResponse
import kiwi.argen.junini.gemini.GemtextLine
import kiwi.argen.junini.gemini.InMemoryKnownHostsStore
import kiwi.argen.junini.gemini.KnownHosts
import kiwi.argen.junini.gemini.KnownHostsStore
import kiwi.argen.junini.gemini.ServerCertificate
import kiwi.argen.junini.gemini.decodeText
import kiwi.argen.junini.gemini.isGemini
import kiwi.argen.junini.gemini.parseGemtext
import kiwi.argen.junini.gemini.parseUserInput
import kiwi.argen.junini.gemini.platformGeminiTransport
import kiwi.argen.junini.gemini.resolveUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowserState(
    val urlInput: String = "",
    val currentUrl: Url? = null,
    val page: PageState = PageState.Idle,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    /** A one-off message for a snackbar; cleared with [BrowserViewModel.noticeShown]. */
    val notice: String? = null,
)

data class HistoryEntry(
    val url: Url,
    val page: PageState,
)

sealed interface PageState {
    data object Idle : PageState
    data class Gemtext(val url: Url, val lines: List<GemtextLine>) : PageState
    data class PlainText(val url: Url, val text: String) : PageState
    data class Message(val title: String, val detail: String) : PageState

    /** [host] presented a certificate that doesn't match the one pinned on an earlier visit. */
    data class CertificateChanged(
        val url: Url,
        val host: String,
        val port: Int,
        val pinned: ServerCertificate,
        val presented: ServerCertificate,
    ) : PageState
}

class BrowserViewModel(
    knownHostsStore: KnownHostsStore = InMemoryKnownHostsStore(),
    private val knownHosts: KnownHosts = KnownHosts(knownHostsStore),
    private val client: GeminiClient = GeminiClient(platformGeminiTransport(knownHosts)),
    private val maxHistorySize: Int = 50,
) : ViewModel() {
    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    private val backStack = mutableListOf<HistoryEntry>()
    private val forwardStack = mutableListOf<HistoryEntry>()
    private var loadJob: Job? = null

    fun onUrlInputChange(value: String) = _state.update { it.copy(urlInput = value) }

    fun submitUrlInput() {
        val url = parseUserInput(_state.value.urlInput)
        if (url == null) {
            _state.update { it.copy(notice = "Enter a gemini:// URL") }
            return
        }
        load(url)
    }

    fun onLinkClick(href: String) {
        val base = _state.value.currentUrl ?: return
        val url = resolveUrl(base, href)
        when {
            url == null -> _state.update { it.copy(notice = "Invalid link \"$href\"") }
            !url.isGemini -> _state.update { it.copy(notice = "Can't open ${url.protocol.name} links yet") }
            else -> load(url)
        }
    }

    fun goBack() {
        if (backStack.isEmpty()) {
            if (_state.value.isLoading) {
                loadJob?.cancel()
                _state.update {
                    it.copy(
                        urlInput = it.currentUrl?.toString().orEmpty(),
                        isLoading = false,
                    )
                }
            }
            return
        }
        loadJob?.cancel()
        val currentUrl = _state.value.currentUrl
        val currentPage = _state.value.page
        if (currentUrl != null && currentPage !is PageState.Idle) {
            forwardStack.add(HistoryEntry(currentUrl, currentPage))
            if (forwardStack.size > maxHistorySize) forwardStack.removeFirst()
        }
        val previous = backStack.removeLast()
        _state.update {
            it.copy(
                urlInput = previous.url.toString(),
                currentUrl = previous.url,
                page = previous.page,
                isLoading = false,
                canGoBack = backStack.isNotEmpty(),
                canGoForward = forwardStack.isNotEmpty(),
            )
        }
    }

    fun goForward() {
        if (forwardStack.isEmpty()) return
        loadJob?.cancel()
        val currentUrl = _state.value.currentUrl
        val currentPage = _state.value.page
        if (currentUrl != null && currentPage !is PageState.Idle) {
            backStack.add(HistoryEntry(currentUrl, currentPage))
            if (backStack.size > maxHistorySize) backStack.removeFirst()
        }
        val next = forwardStack.removeLast()
        _state.update {
            it.copy(
                urlInput = next.url.toString(),
                currentUrl = next.url,
                page = next.page,
                isLoading = false,
                canGoBack = backStack.isNotEmpty(),
                canGoForward = forwardStack.isNotEmpty(),
            )
        }
    }

    fun noticeShown() = _state.update { it.copy(notice = null) }

    /** Pins the new certificate shown by a [PageState.CertificateChanged] page and loads the page again. */
    fun trustNewCertificate() {
        val page = _state.value.page as? PageState.CertificateChanged ?: return
        knownHosts.trust(page.host, page.port, page.presented)
        load(page.url)
    }

    /** Leaves a [PageState.CertificateChanged] page without trusting the new certificate. */
    fun cancelCertificateChange() {
        if (_state.value.page !is PageState.CertificateChanged) return
        if (backStack.isNotEmpty()) {
            goBack()
        } else {
            _state.update { it.copy(urlInput = "", currentUrl = null, page = PageState.Idle) }
        }
    }

    private fun load(url: Url) {
        loadJob?.cancel()
        val previousUrl = _state.value.currentUrl
        val previousPage = _state.value.page
        _state.update { it.copy(urlInput = url.toString(), isLoading = true) }
        loadJob = viewModelScope.launch {
            val result = try {
                val response = client.fetch(url)
                response.url to response.toPageState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: CertificateMismatchException) {
                url to PageState.CertificateChanged(url, e.host, e.port, e.pinned, e.presented)
            } catch (e: Exception) {
                url to PageState.Message("Couldn't load page", e.message ?: e::class.simpleName.orEmpty())
            }
            val (finalUrl, page) = result
            // A certificate prompt isn't a page worth going back to.
            val keepPrevious = previousPage !is PageState.Idle && previousPage !is PageState.CertificateChanged
            if (previousUrl != null && keepPrevious && previousUrl != finalUrl) {
                backStack.add(HistoryEntry(previousUrl, previousPage))
                if (backStack.size > maxHistorySize) backStack.removeFirst()
                forwardStack.clear()
            }
            _state.update {
                it.copy(
                    urlInput = finalUrl.toString(),
                    currentUrl = finalUrl,
                    page = page,
                    isLoading = false,
                    canGoBack = backStack.isNotEmpty(),
                    canGoForward = forwardStack.isNotEmpty(),
                )
            }
        }
    }
}

private fun GeminiResponse.toPageState(): PageState = when (this) {
    is GeminiResponse.Success -> when {
        mimeType.isGemtext -> PageState.Gemtext(url, parseGemtext(decodeText()))
        mimeType.isText -> PageState.PlainText(url, decodeText())
        else -> PageState.Message("Unsupported content", "Can't display $mimeType content yet")
    }
    is GeminiResponse.Input -> PageState.Message(
        "Input requested",
        "${prompt.ifBlank { "The server asked for input." }}\n\nSending input isn't supported yet.",
    )
    is GeminiResponse.Redirect -> PageState.Message("Redirect", "This page redirects to $target, which isn't a gemini:// URL.")
    is GeminiResponse.Failure -> PageState.Message("Error $status", message)
}
