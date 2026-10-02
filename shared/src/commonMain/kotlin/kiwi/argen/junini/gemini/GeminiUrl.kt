package kiwi.argen.junini.gemini

import io.ktor.http.DEFAULT_PORT
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
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
    val reference = href.trim()
    val builder = URLBuilder(base)
    // Only an empty or fragment-only reference keeps the base's query (RFC 3986 section 5.2.2). takeFrom appends
    // to the existing parameters instead of replacing them, so clear them first.
    if (reference.isNotEmpty() && !reference.startsWith('#')) builder.parameters.clear()
    builder.takeFrom(reference)
    builder.pathSegments = removeDotSegments(builder.pathSegments)
    builder.build().withRootPath()
}.getOrNull()

/**
 * This URL with [input] as its whole query, the way a Gemini server expects the answer to an input request:
 * percent-encoded (spaces as `%20`) rather than form-encoded. Any existing query and fragment are dropped.
 */
fun Url.withQuery(input: String): Url {
    val base = URLBuilder(this).apply {
        fragment = ""
        parameters.clear()
    }.build().toString().substringBefore('?')
    return Url("$base?${input.encodeURLParameter()}")
}

/** This URL without its query and fragment. */
fun Url.withoutQuery(): Url = URLBuilder(this).apply {
    fragment = ""
    parameters.clear()
}.build()

val Url.isGemini: Boolean get() = protocol.name.equals(GEMINI_SCHEME, ignoreCase = true)

/** The port to connect to: the one in the URL, or Gemini's default when none is given. */
val Url.geminiPort: Int get() = specifiedPort.takeIf { it != DEFAULT_PORT } ?: GEMINI_DEFAULT_PORT

/**
 * A canonical form for comparing URLs: lowercase host, no explicit default port, no fragment and a
 * root path, so `gemini://Host:1965` and `gemini://host/#top` both become `gemini://host/`.
 */
fun Url.normalized(): String = URLBuilder(this).apply {
    host = host.lowercase()
    if (isGemini && port == GEMINI_DEFAULT_PORT) port = DEFAULT_PORT
    fragment = ""
}.build().withRootPath().toString()

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
