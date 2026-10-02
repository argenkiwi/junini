package kiwi.argen.junini.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.http.Url
import kiwi.argen.junini.gemini.CertificateMismatchException
import kiwi.argen.junini.gemini.GEMINI_DEFAULT_PORT
import kiwi.argen.junini.gemini.GeminiClient
import kiwi.argen.junini.gemini.GeminiResponse
import kiwi.argen.junini.gemini.GemtextLine
import kiwi.argen.junini.gemini.InMemoryKnownHostsStore
import kiwi.argen.junini.gemini.KnownHosts
import kiwi.argen.junini.gemini.KnownHostsStore
import kiwi.argen.junini.gemini.ServerCertificate
import kiwi.argen.junini.gemini.decodeText
import kiwi.argen.junini.gemini.geminiPort
import kiwi.argen.junini.gemini.isGemini
import kiwi.argen.junini.gemini.parseGemtext
import kiwi.argen.junini.gemini.parseUserInput
import kiwi.argen.junini.gemini.platformGeminiTransport
import kiwi.argen.junini.gemini.resolveUrl
import kiwi.argen.junini.gemini.withQuery
import kiwi.argen.junini.history.BrowsingHistory
import kiwi.argen.junini.history.HistoryStore
import kiwi.argen.junini.history.InMemoryHistoryStore
import kiwi.argen.junini.identity.Identity
import kiwi.argen.junini.identity.IdentityManager
import kiwi.argen.junini.identity.InMemoryIdentityStore
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
    /** Visited URLs matching [urlInput] while the user types, best match first. */
    val suggestions: List<String> = emptyList(),
    /** A one-off message for a snackbar; cleared with [BrowserViewModel.noticeShown]. */
    val notice: String? = null,
    /** Whether this platform can manage identities at all. */
    val identitiesAvailable: Boolean = false,
    /** The identities screen's contents while it is open, otherwise null. */
    val identities: List<IdentityItem>? = null,
)

/** An [identity] and the capsules it is assigned to. */
data class IdentityItem(val identity: Identity, val hosts: List<IdentityHost>)

/** A capsule an identity is assigned to. Kept as host and port, not text, so IPv6 hosts stay unambiguous. */
data class IdentityHost(val host: String, val port: Int) {
    /** `host`, `host:port` for a non-default port, with IPv6 hosts in brackets. */
    val label: String
        get() {
            val name = if (':' in host) "[$host]" else host
            return if (port == GEMINI_DEFAULT_PORT) name else "$name:$port"
        }
}

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

    /**
     * [host] answered with status 60, 61 or 62. [current] is the identity assigned to the host, if any
     * (it was rejected for 61 and 62), and [available] are all the identities the user could pick instead.
     */
    data class ClientCertificateRequired(
        val url: Url,
        val host: String,
        val port: Int,
        val status: Int,
        val message: String,
        val current: Identity?,
        val available: List<Identity>,
    ) : PageState

    /**
     * The server at [url] asked for input (status 10, or 11 when [sensitive]) and showed [prompt].
     * [attempt] makes each prompt distinct, so a repeated identical prompt still resets the sheet.
     */
    data class Input(val url: Url, val prompt: String, val sensitive: Boolean, val attempt: Int = 0) : PageState
}

class BrowserViewModel(
    knownHostsStore: KnownHostsStore = InMemoryKnownHostsStore(),
    private val knownHosts: KnownHosts = KnownHosts(knownHostsStore),
    private val identityManager: IdentityManager? = null,
    private val client: GeminiClient = GeminiClient(
        platformGeminiTransport(knownHosts, identityManager?.store ?: InMemoryIdentityStore()),
    ),
    private val maxHistorySize: Int = 50,
    historyStore: HistoryStore = InMemoryHistoryStore(),
    private val history: BrowsingHistory = BrowsingHistory(historyStore),
) : ViewModel() {
    private val _state = MutableStateFlow(BrowserState(identitiesAvailable = identityManager != null))
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    private val backStack = mutableListOf<HistoryEntry>()
    private val forwardStack = mutableListOf<HistoryEntry>()
    private var loadJob: Job? = null
    private var inputAttempts = 0

    fun onUrlInputChange(value: String) =
        _state.update { it.copy(urlInput = value, suggestions = history.suggest(value)) }

    fun pickSuggestion(url: String) {
        parseUserInput(url)?.let(::load)
    }

    fun dismissSuggestions() = _state.update { it.copy(suggestions = emptyList()) }

    fun clearHistory() {
        history.clear()
        _state.update { it.copy(suggestions = emptyList(), notice = "History cleared") }
    }

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
                        suggestions = emptyList(),
                        isLoading = false,
                    )
                }
            }
            return
        }
        loadJob?.cancel()
        val currentUrl = _state.value.currentUrl
        val currentPage = _state.value.page
        if (currentUrl != null && currentPage !is PageState.Idle && currentPage !is PageState.Input) {
            forwardStack.add(HistoryEntry(currentUrl, currentPage))
            if (forwardStack.size > maxHistorySize) forwardStack.removeFirst()
        }
        val previous = backStack.removeLast()
        _state.update {
            it.copy(
                urlInput = previous.url.toString(),
                suggestions = emptyList(),
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
        if (currentUrl != null && currentPage !is PageState.Idle && currentPage !is PageState.Input) {
            backStack.add(HistoryEntry(currentUrl, currentPage))
            if (backStack.size > maxHistorySize) backStack.removeFirst()
        }
        val next = forwardStack.removeLast()
        _state.update {
            it.copy(
                urlInput = next.url.toString(),
                suggestions = emptyList(),
                currentUrl = next.url,
                page = next.page,
                isLoading = false,
                canGoBack = backStack.isNotEmpty(),
                canGoForward = forwardStack.isNotEmpty(),
            )
        }
    }

    fun noticeShown() = _state.update { it.copy(notice = null) }

    fun openIdentities() = _state.update { it.copy(identities = identityItems()) }

    fun closeIdentities() = _state.update { it.copy(identities = null) }

    /** Imports the identity in [texts], the text of one PEM file with a certificate and key or of a certificate file and a key file. */
    fun importIdentity(texts: List<String>) = manageIdentities("Identity imported") { it.import(texts) }

    /** The identity as PEM text, or null (with a notice) if it couldn't be exported. */
    fun exportIdentity(id: String): String? {
        val manager = identityManager ?: return null
        return try {
            manager.export(id)
        } catch (e: Exception) {
            _state.update { it.copy(notice = e.message ?: "Couldn't export the identity") }
            null
        }
    }

    fun deleteIdentity(id: String) = manageIdentities("Identity deleted") { it.delete(id) }

    /** Assigns [id] to the capsule in [hostInput], which can be a host, `host:port` or a gemini:// URL. */
    fun assignIdentity(hostInput: String, id: String) {
        val url = parseUserInput(hostInput)
        if (url == null) {
            _state.update { it.copy(notice = "Enter a capsule address") }
            return
        }
        manageIdentities("Identity assigned to ${url.host}") { it.assign(url.host, url.geminiPort, id) }
    }

    fun unassignIdentity(host: String, port: Int) =
        manageIdentities("Identity unassigned from $host") { it.unassign(host, port) }

    /** Assigns [id] to the host behind a [PageState.ClientCertificateRequired] page and loads the page again. */
    fun useIdentity(id: String) {
        val page = _state.value.page as? PageState.ClientCertificateRequired ?: return
        // Reloading after a failed change would only fetch the same rejection again and bury the notice.
        if (manageIdentities(null) { it.assign(page.host, page.port, id) }) load(page.url)
    }

    /** Imports the identity in [texts], assigns it to the host behind a [PageState.ClientCertificateRequired] page and reloads. */
    fun importIdentityForPage(texts: List<String>) {
        val page = _state.value.page as? PageState.ClientCertificateRequired ?: return
        val manager = identityManager ?: return
        try {
            manager.assign(page.host, page.port, manager.import(texts).id)
        } catch (e: Exception) {
            _state.update { it.copy(notice = e.message ?: "Couldn't import the identity") }
            return
        }
        load(page.url)
    }

    /** Stops using the rejected identity for the host behind a [PageState.ClientCertificateRequired] page. */
    fun unassignIdentityForPage() {
        val page = _state.value.page as? PageState.ClientCertificateRequired ?: return
        if (manageIdentities(null) { it.unassign(page.host, page.port) }) load(page.url)
    }

    private fun identityItems(): List<IdentityItem> {
        val manager = identityManager ?: return emptyList()
        val hostsById = manager.assignments().entries.groupBy({ it.value }, { (key, _) -> IdentityHost(key.first, key.second) })
        val order = compareBy<IdentityHost>({ it.host }, { it.port })
        return manager.identities().map { IdentityItem(it, hostsById[it.id].orEmpty().sortedWith(order)) }
    }

    /** Runs [action] and refreshes the identities screen. Returns false (with a notice) if it failed. */
    private fun manageIdentities(message: String?, action: (IdentityManager) -> Unit): Boolean {
        val manager = identityManager ?: return false
        return try {
            action(manager)
            _state.update {
                it.copy(
                    identities = if (it.identities != null) identityItems() else null,
                    notice = message ?: it.notice,
                )
            }
            true
        } catch (e: Exception) {
            _state.update { it.copy(notice = e.message ?: "Something went wrong with the identity") }
            false
        }
    }

    /** Pins the new certificate shown by a [PageState.CertificateChanged] page and loads the page again. */
    fun trustNewCertificate() {
        val page = _state.value.page as? PageState.CertificateChanged ?: return
        knownHosts.trust(page.host, page.port, page.presented)
        load(page.url)
    }

    /** Answers a [PageState.Input] page by requesting its URL again with [text] as the query. */
    fun submitInput(text: String) {
        val page = _state.value.page as? PageState.Input ?: return
        load(page.url.withQuery(text))
    }

    /** Leaves a [PageState.CertificateChanged], [PageState.ClientCertificateRequired] or [PageState.Input] page without acting on it. */
    fun cancelCertificateChange() {
        val page = _state.value.page
        if (page !is PageState.CertificateChanged && page !is PageState.ClientCertificateRequired && page !is PageState.Input) return
        if (backStack.isNotEmpty()) {
            goBack()
        } else {
            _state.update { it.copy(urlInput = "", suggestions = emptyList(), currentUrl = null, page = PageState.Idle) }
        }
    }

    private fun GeminiResponse.toPageState(): PageState {
        if (this is GeminiResponse.Failure && status in 60..62) {
            val manager = identityManager
            if (manager != null) {
                val port = url.geminiPort
                return PageState.ClientCertificateRequired(
                    url, url.host, port, status, message, manager.assignedTo(url.host, port), manager.identities(),
                )
            }
        }
        return toBasicPageState()
    }

    private fun load(url: Url) {
        loadJob?.cancel()
        val previousUrl = _state.value.currentUrl
        val previousPage = _state.value.page
        _state.update { it.copy(urlInput = url.toString(), suggestions = emptyList(), isLoading = true) }
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
            val (finalUrl, loaded) = result
            // Every prompt is a new one, even if the server asks the same thing again, so the sheet starts empty.
            val page = if (loaded is PageState.Input) loaded.copy(attempt = ++inputAttempts) else loaded
            // Only pages that actually rendered are worth suggesting again.
            if (page is PageState.Gemtext || page is PageState.PlainText) history.record(finalUrl)
            // A certificate or input prompt isn't a page worth going back to.
            val keepPrevious = previousPage !is PageState.Idle &&
                previousPage !is PageState.CertificateChanged &&
                previousPage !is PageState.ClientCertificateRequired &&
                previousPage !is PageState.Input
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

private fun GeminiResponse.toBasicPageState(): PageState = when (this) {
    is GeminiResponse.Success -> when {
        mimeType.isGemtext -> PageState.Gemtext(url, parseGemtext(decodeText()))
        mimeType.isText -> PageState.PlainText(url, decodeText())
        else -> PageState.Message("Unsupported content", "Can't display $mimeType content yet")
    }
    is GeminiResponse.Input -> PageState.Input(url, prompt, sensitive)
    is GeminiResponse.Redirect -> PageState.Message("Redirect", "This page redirects to $target, which isn't a gemini:// URL.")
    is GeminiResponse.Failure -> PageState.Message("Error $status", message)
}
