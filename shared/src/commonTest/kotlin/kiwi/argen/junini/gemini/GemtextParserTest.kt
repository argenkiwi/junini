package kiwi.argen.junini.gemini

import kotlin.test.Test
import kotlin.test.assertEquals

class GemtextParserTest {

    @Test
    fun parsesEveryLineType() {
        val source = """
            # Title
            ## Section
            ### Subsection
            Some text

            => gemini://example.org/ Example
            => /relative
            =>   spaced.gmi   Spaced   label
            * item
            > quote
        """.trimIndent()

        assertEquals(
            listOf(
                GemtextLine.Heading(1, "Title"),
                GemtextLine.Heading(2, "Section"),
                GemtextLine.Heading(3, "Subsection"),
                GemtextLine.Text("Some text"),
                GemtextLine.Text(""),
                GemtextLine.Link("gemini://example.org/", "Example"),
                GemtextLine.Link("/relative", null),
                GemtextLine.Link("spaced.gmi", "Spaced   label"),
                GemtextLine.ListItem("item"),
                GemtextLine.Quote("quote"),
            ),
            parseGemtext(source),
        )
    }

    @Test
    fun preformattedBlocksKeepContentVerbatim() {
        val source = "```ascii art\n# not a heading\n=> not a link\n```\nafter"

        assertEquals(
            listOf(
                GemtextLine.Preformatted("ascii art", listOf("# not a heading", "=> not a link")),
                GemtextLine.Text("after"),
            ),
            parseGemtext(source),
        )
    }

    @Test
    fun unterminatedPreformattedBlockRunsToTheEnd() {
        assertEquals(
            listOf(GemtextLine.Preformatted(null, listOf("a", "b"))),
            parseGemtext("```\na\nb"),
        )
    }

    @Test
    fun handlesCrlfAndTrailingNewline() {
        assertEquals(
            listOf(GemtextLine.Heading(1, "Hi"), GemtextLine.Text("there")),
            parseGemtext("# Hi\r\nthere\r\n"),
        )
    }

    @Test
    fun bareArrowIsText() {
        assertEquals(listOf(GemtextLine.Text("=>")), parseGemtext("=>"))
    }

    @Test
    fun asteriskWithoutSpaceIsText() {
        assertEquals(listOf(GemtextLine.Text("*bold*")), parseGemtext("*bold*"))
    }
}
