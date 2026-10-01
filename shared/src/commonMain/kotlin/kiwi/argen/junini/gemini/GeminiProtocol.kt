package kiwi.argen.junini.gemini

import io.ktor.http.Url

/** Longest request URL allowed by the spec, in bytes. */
const val MAX_REQUEST_URL_BYTES = 1024

/** Status line limit: two digits, a space, up to 1024 bytes of meta, then CRLF. */
private const val MAX_HEADER_BYTES = 2 + 1 + 1024 + 2

open class GeminiException(message: String) : Exception(message)

sealed interface GeminiResponse {
    val url: Url

    /** 1x: the server wants a query string, e.g. a search term. */
    data class Input(override val url: Url, val prompt: String, val sensitive: Boolean) : GeminiResponse

    /** 2x: [body] holds the document, typed by [mimeType]. */
    class Success(override val url: Url, val mimeType: MimeType, val body: ByteArray) : GeminiResponse

    /** 3x: [target] is the (possibly relative) URL to go to next. */
    data class Redirect(override val url: Url, val target: String, val permanent: Boolean) : GeminiResponse

    /** 4x, 5x and 6x: temporary/permanent failures and client-certificate requests. */
    data class Failure(override val url: Url, val status: Int, val message: String) : GeminiResponse
}

data class MimeType(val type: String, val subtype: String, val parameters: Map<String, String>) {
    val charset: String get() = parameters["charset"] ?: "utf-8"
    val isGemtext: Boolean get() = type == "text" && subtype == "gemini"
    val isText: Boolean get() = type == "text"

    override fun toString(): String = "$type/$subtype"

    companion object {
        val Gemtext = MimeType("text", "gemini", mapOf("charset" to "utf-8"))

        /** Parses a `type/subtype; key=value` string. An empty [meta] means `text/gemini`, per spec. */
        fun parse(meta: String): MimeType {
            if (meta.isBlank()) return Gemtext
            val parts = meta.split(';')
            val (type, subtype) = parts.first().trim().lowercase().split('/', limit = 2)
                .takeIf { it.size == 2 && it.all(String::isNotEmpty) }
                ?: throw GeminiException("Invalid MIME type \"$meta\"")
            val parameters = parts.drop(1).mapNotNull { param ->
                val (key, value) = param.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                key.trim().lowercase() to value.trim().trim('"')
            }.toMap()
            return MimeType(type, subtype, parameters)
        }
    }
}

data class GeminiHeader(val status: Int, val meta: String)

fun requestLine(url: Url): String {
    val absolute = url.toString()
    if (absolute.encodeToByteArray().size > MAX_REQUEST_URL_BYTES) {
        throw GeminiException("URL is longer than $MAX_REQUEST_URL_BYTES bytes")
    }
    return "$absolute\r\n"
}

/** Parses the status line at the start of [raw] and returns the header and the byte offset where the body starts. */
fun parseHeader(raw: ByteArray): Pair<GeminiHeader, Int> {
    val lf = raw.indexOfFirst { it == '\n'.code.toByte() }
    if (lf == -1 || lf > MAX_HEADER_BYTES) throw GeminiException("Malformed response header")
    val end = if (lf > 0 && raw[lf - 1] == '\r'.code.toByte()) lf - 1 else lf
    return parseHeaderLine(raw.decodeToString(0, end)) to lf + 1
}

fun parseHeaderLine(line: String): GeminiHeader {
    if (line.length < 2 || !line[0].isDigit() || !line[1].isDigit()) {
        throw GeminiException("Malformed status line \"$line\"")
    }
    if (line.length > 2 && line[2] != ' ') throw GeminiException("Malformed status line \"$line\"")
    return GeminiHeader(status = line.substring(0, 2).toInt(), meta = line.drop(3))
}

fun toResponse(url: Url, header: GeminiHeader, body: ByteArray): GeminiResponse = when (header.status / 10) {
    1 -> GeminiResponse.Input(url, prompt = header.meta, sensitive = header.status == 11)
    2 -> GeminiResponse.Success(url, MimeType.parse(header.meta), body)
    3 -> GeminiResponse.Redirect(url, target = header.meta, permanent = header.status == 31)
    4, 5, 6 -> GeminiResponse.Failure(url, header.status, header.meta.ifBlank { defaultFailureMessage(header.status) })
    else -> throw GeminiException("Unknown status code ${header.status}")
}

private fun defaultFailureMessage(status: Int): String = when (status) {
    40 -> "Temporary failure"
    41 -> "Server unavailable"
    42 -> "CGI error"
    43 -> "Proxy error"
    44 -> "Slow down"
    50 -> "Permanent failure"
    51 -> "Not found"
    52 -> "Gone"
    53 -> "Proxy request refused"
    59 -> "Bad request"
    60 -> "Client certificate required"
    61 -> "Certificate not authorised"
    62 -> "Certificate not valid"
    else -> "Request failed"
}
