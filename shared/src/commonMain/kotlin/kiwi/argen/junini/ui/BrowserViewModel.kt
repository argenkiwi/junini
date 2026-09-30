package kiwi.argen.junini.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.http.Url
import kiwi.argen.junini.gemini.GeminiClient
import kiwi.argen.junini.gemini.GeminiResponse
import kiwi.argen.junini.gemini.GemtextLine
import kiwi.argen.junini.gemini.decodeText
import kiwi.argen.junini.gemini.isGemini
import kiwi.argen.junini.gemini.parseGemtext
import kiwi.argen.junini.gemini.parseUserInput
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
    /** A one-off message for a snackbar; cleared with [BrowserViewModel.noticeShown]. */
    val notice: String? = null,
)

sealed interface PageState {
    data object Idle : PageState
    data class Gemtext(val url: Url, val lines: List<GemtextLine>) : PageState
    data class PlainText(val url: Url, val text: String) : PageState
    data class Message(val title: String, val detail: String) : PageState
}

class BrowserViewModel(
    private val client: GeminiClient = GeminiClient(),
) : ViewModel() {
    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()

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

    fun noticeShown() = _state.update { it.copy(notice = null) }

    private fun load(url: Url) {
        loadJob?.cancel()
        _state.update { it.copy(urlInput = url.toString(), isLoading = true) }
        loadJob = viewModelScope.launch {
            val result = try {
                val response = client.fetch(url)
                response.url to response.toPageState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                url to PageState.Message("Couldn't load page", e.message ?: e::class.simpleName.orEmpty())
            }
            val (finalUrl, page) = result
            _state.update {
                it.copy(urlInput = finalUrl.toString(), currentUrl = finalUrl, page = page, isLoading = false)
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
