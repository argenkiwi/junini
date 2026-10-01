package kiwi.argen.junini.gemini

import io.ktor.http.Url

class GeminiClient(
    private val transport: GeminiTransport,
    private val maxRedirects: Int = 5,
) {
    /**
     * Fetches [url], following Gemini redirects. A redirect to another scheme is returned
     * as a [GeminiResponse.Redirect] for the caller to deal with.
     */
    suspend fun fetch(url: Url): GeminiResponse {
        var current = url
        val visited = mutableSetOf<String>()
        while (true) {
            if (!visited.add(current.toString())) throw GeminiException("Redirect loop at $current")
            val response = fetchOnce(current)
            if (response !is GeminiResponse.Redirect) return response
            val next = resolveUrl(current, response.target)
                ?: throw GeminiException("Invalid redirect target \"${response.target}\"")
            if (!next.isGemini) return response
            if (visited.size > maxRedirects) throw GeminiException("Too many redirects")
            current = next
        }
    }

    private suspend fun fetchOnce(url: Url): GeminiResponse {
        require(url.isGemini) { "Not a gemini URL: $url" }
        val raw = transport.fetch(url.host, url.geminiPort, requestLine(url))
        val (header, bodyStart) = parseHeader(raw)
        return toResponse(url, header, raw.copyOfRange(bodyStart, raw.size))
    }
}

/** Decodes a text body. Only UTF-8 and its ASCII subset are supported for now. */
fun GeminiResponse.Success.decodeText(): String {
    val charset = mimeType.charset.lowercase()
    if (charset !in setOf("utf-8", "utf8", "us-ascii", "ascii")) {
        throw GeminiException("Unsupported charset \"$charset\"")
    }
    return body.decodeToString()
}
