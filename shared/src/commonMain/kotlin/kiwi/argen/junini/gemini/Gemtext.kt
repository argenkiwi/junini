package kiwi.argen.junini.gemini

sealed interface GemtextLine {
    data class Text(val text: String) : GemtextLine
    data class Link(val url: String, val label: String?) : GemtextLine
    data class Heading(val level: Int, val text: String) : GemtextLine
    data class ListItem(val text: String) : GemtextLine
    data class Quote(val text: String) : GemtextLine
    data class Preformatted(val alt: String?, val lines: List<String>) : GemtextLine
}

private const val PREFORMAT_TOGGLE = "```"

fun parseGemtext(source: String): List<GemtextLine> {
    val result = mutableListOf<GemtextLine>()
    var preformatAlt: String? = null
    var preformatLines: MutableList<String>? = null

    for (rawLine in source.lineSequence()) {
        val line = rawLine.removeSuffix("\r")
        val block = preformatLines
        if (block != null) {
            if (line.startsWith(PREFORMAT_TOGGLE)) {
                result += GemtextLine.Preformatted(preformatAlt, block)
                preformatLines = null
            } else {
                block += line
            }
            continue
        }
        when {
            line.startsWith(PREFORMAT_TOGGLE) -> {
                preformatAlt = line.removePrefix(PREFORMAT_TOGGLE).trim().ifEmpty { null }
                preformatLines = mutableListOf()
            }
            line.startsWith("=>") -> result += parseLink(line) ?: GemtextLine.Text(line)
            line.startsWith("###") -> result += GemtextLine.Heading(3, line.drop(3).trim())
            line.startsWith("##") -> result += GemtextLine.Heading(2, line.drop(2).trim())
            line.startsWith("#") -> result += GemtextLine.Heading(1, line.drop(1).trim())
            line.startsWith("* ") -> result += GemtextLine.ListItem(line.drop(2).trim())
            line.startsWith(">") -> result += GemtextLine.Quote(line.drop(1).trim())
            else -> result += GemtextLine.Text(line)
        }
    }
    // An unterminated block runs to the end of the document.
    preformatLines?.let { result += GemtextLine.Preformatted(preformatAlt, it) }

    // lineSequence yields an empty last line for a trailing newline; don't render it.
    if (result.lastOrNull() == GemtextLine.Text("")) result.removeAt(result.lastIndex)
    return result
}

private fun parseLink(line: String): GemtextLine.Link? {
    val rest = line.drop(2).trim()
    if (rest.isEmpty()) return null
    val split = rest.indexOfFirst { it.isWhitespace() }
    return if (split == -1) {
        GemtextLine.Link(rest, null)
    } else {
        GemtextLine.Link(rest.substring(0, split), rest.substring(split).trim().ifEmpty { null })
    }
}
