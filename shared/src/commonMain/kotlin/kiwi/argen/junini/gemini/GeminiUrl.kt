package kiwi.argen.junini.gemini

import io.ktor.http.DEFAULT_PORT
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodedPath
import io.ktor.http.takeFrom

const val GEMINI_SCHEME = "gemini"
const val GEMINI_DEFAULT_PORT = 1965

/**
 * Turns what the user typed into an absolute `gemini://` URL, adding the scheme when it's missing.
 * Returns `null` when the input is blank, has no host, or uses another scheme.
 */
fun parseUserInput(input: String): Url? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val withScheme = if ("://" in trimmed) trimmed else "$GEMINI_SCHEME://$trimmed"
    val url = runCatching { Url(withScheme) }.getOrNull() ?: return null
    if (!url.isGemini || url.host.isEmpty()) return null
    return url.withRootPath()
}

/** Resolves [href] (as found in a link line or a redirect) against [base], per RFC 3986. */
fun resolveUrl(base: Url, href: String): Url? = runCatching {
    val builder = URLBuilder(base).takeFrom(href.trim())
    builder.pathSegments = removeDotSegments(builder.pathSegments)
    builder.build().withRootPath()
}.getOrNull()

val Url.isGemini: Boolean get() = protocol.name.equals(GEMINI_SCHEME, ignoreCase = true)

/** The port to connect to: the one in the URL, or Gemini's default when none is given. */
val Url.geminiPort: Int get() = specifiedPort.takeIf { it != DEFAULT_PORT } ?: GEMINI_DEFAULT_PORT

/** `gemini://host` and `gemini://host/` are the same resource; always send the latter. */
private fun Url.withRootPath(): Url =
    if (isGemini && encodedPath.isEmpty()) URLBuilder(this).apply { encodedPath = "/" }.build() else this

private fun removeDotSegments(segments: List<String>): List<String> {
    if (segments.none { it == "." || it == ".." }) return segments
    val output = ArrayList<String>(segments.size)
    segments.forEachIndexed { index, segment ->
        val isLast = index == segments.lastIndex
        when (segment) {
            "." -> if (isLast) output.add("")
            ".." -> {
                // Never pop the leading empty segment that marks an absolute path.
                if (output.size > 1) output.removeAt(output.lastIndex)
                if (isLast) output.add("")
            }
            else -> output.add(segment)
        }
    }
    return output
}
