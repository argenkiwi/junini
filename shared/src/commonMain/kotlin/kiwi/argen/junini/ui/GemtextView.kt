package kiwi.argen.junini.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kiwi.argen.junini.gemini.GemtextLine

/** Keeps lines readable on wide windows (desktop, tablets). */
private val MaxContentWidth = 720.dp
private val HorizontalMargin = 16.dp

@Composable
fun GemtextView(
    lines: List<GemtextLine>,
    onLinkClick: (String) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    PageColumn(listState, contentPadding) {
        items(lines) { line ->
            Box(Modifier.widthIn(max = MaxContentWidth).fillMaxWidth()) {
                GemtextLineView(line, onLinkClick)
            }
        }
    }
}

@Composable
fun PlainTextView(text: String, listState: LazyListState, contentPadding: PaddingValues) {
    PageColumn(listState, contentPadding) {
        items(text.lines()) { line ->
            Text(
                text = line,
                modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
fun MessageView(title: String, detail: String, contentPadding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 480.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PageColumn(
    listState: LazyListState,
    contentPadding: PaddingValues,
    content: LazyListScope.() -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection) + HorizontalMargin,
            end = contentPadding.calculateEndPadding(layoutDirection) + HorizontalMargin,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun GemtextLineView(line: GemtextLine, onLinkClick: (String) -> Unit) {
    val typography = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    when (line) {
        is GemtextLine.Text ->
            if (line.text.isBlank()) {
                Spacer(Modifier.height(12.dp))
            } else {
                Text(line.text, style = typography.bodyLarge, modifier = Modifier.padding(vertical = 2.dp))
            }

        is GemtextLine.Heading -> Text(
            text = line.text,
            style = when (line.level) {
                1 -> typography.headlineMedium
                2 -> typography.headlineSmall
                else -> typography.titleLarge
            },
            color = colors.onSurface,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )

        is GemtextLine.Link -> Text(
            text = line.label ?: line.url,
            style = typography.bodyLarge,
            color = colors.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onLinkClick(line.url) }
                .padding(horizontal = 4.dp, vertical = 8.dp),
        )

        is GemtextLine.ListItem -> Row(Modifier.padding(vertical = 2.dp)) {
            Text("•", style = typography.bodyLarge, modifier = Modifier.width(20.dp), textAlign = TextAlign.Center)
            Text(line.text, style = typography.bodyLarge)
        }

        is GemtextLine.Quote -> Row(
            modifier = Modifier.padding(vertical = 4.dp).height(IntrinsicSize.Min),
        ) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(colors.outlineVariant, RoundedCornerShape(2.dp)))
            Text(
                text = line.text,
                style = typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        is GemtextLine.Preformatted -> Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .semantics { line.alt?.let { contentDescription = it } },
            shape = MaterialTheme.shapes.medium,
            color = colors.surfaceContainerHighest,
        ) {
            Text(
                text = line.lines.joinToString("\n"),
                style = typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
            )
        }
    }
}
