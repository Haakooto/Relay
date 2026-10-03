package com.relay.app.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.relay.app.R
import java.util.Locale

/** Opens the guide at a section; provided by the navigation host. */
val LocalOpenGuide = compositionLocalOf<(GuideSection?) -> Unit> { {} }

/** The ? in a pane's header: opens the guide at that pane's section. */
@Composable
fun GuideButton(section: GuideSection) {
    val open = LocalOpenGuide.current
    IconButton(onClick = { open(section) }) {
        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = stringResource(R.string.guide_title))
    }
}

/**
 * The usage guide, in the app's language: `docs/usage-guide.md` or `.nn.md`, bundled at build
 * time. Opens at [section] when given.
 */
@Composable
fun GuideScreen(section: GuideSection?, onBack: () -> Unit) {
    val context = LocalContext.current
    val blocks = remember {
        val norwegian = Locale.getDefault().language in setOf("nn", "nb", "no")
        val name = if (norwegian) "guide/usage-guide.nn.md" else "guide/usage-guide.md"
        runCatching { context.assets.open(name).bufferedReader().use { it.readText() } }
            .map(GuideMarkdown::parse).getOrDefault(emptyList())
    }
    val list = rememberLazyListState()
    LaunchedEffect(blocks, section) {
        // +1 for the top bar item.
        section?.let { GuideMarkdown.sectionStart(blocks, it.ordinal) }?.let { list.scrollToItem(it + 1) }
    }
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back)) }
                Text(stringResource(R.string.guide_title), style = MaterialTheme.typography.titleLarge)
            }
        }
        itemsIndexed(blocks) { i, block ->
            // The file's own # title duplicates the top bar.
            if (i == 0 && block is GuideBlock.Heading && block.level == 1) return@itemsIndexed
            Block(block)
        }
    }
}

@Composable
private fun Block(block: GuideBlock) {
    val colors = MaterialTheme.colorScheme
    when (block) {
        is GuideBlock.Heading -> Text(
            inline(block.text, colors.surfaceContainerHigh),
            style = if (block.level <= 2) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
            color = if (block.level <= 2) colors.primary else colors.onSurface,
            modifier = Modifier.padding(top = if (block.level <= 2) 20.dp else 14.dp, bottom = 6.dp),
        )
        is GuideBlock.Paragraph -> Text(
            inline(block.text, colors.surfaceContainerHigh), style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        is GuideBlock.Bullets -> Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEach { item ->
                Row {
                    Text("•  ", style = MaterialTheme.typography.bodyLarge, color = colors.primary)
                    Text(inline(item, colors.surfaceContainerHigh), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        is GuideBlock.Code -> Text(
            block.text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                .background(colors.surfaceContainerHigh, RoundedCornerShape(8.dp))
                .horizontalScroll(rememberScrollState()).padding(12.dp),
        )
        is GuideBlock.Table -> Column(
            Modifier.fillMaxWidth().padding(bottom = 10.dp).border(1.dp, colors.outlineVariant, RoundedCornerShape(8.dp)),
        ) {
            // Narrow screens: each row as "first column" over "second column".
            block.rows.forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider(color = colors.outlineVariant)
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(inline(row.getOrElse(0) { "" }, colors.surfaceContainerHigh), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(inline(row.drop(1).joinToString(" "), colors.surfaceContainerHigh), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        GuideBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 8.dp), color = colors.outlineVariant)
    }
}

/** `**bold**` and `` `code` `` inside a line of guide text. */
private fun inline(text: String, codeBackground: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end < 0) { append(text.substring(i)); break }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                i = end + 2
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end < 0) { append(text.substring(i)); break }
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) { append(text.substring(i + 1, end)) }
                i = end + 1
            }
            text[i] == '*' && i + 1 < text.length && text[i + 1] != ' ' -> {
                val end = text.indexOf('*', i + 1)
                if (end < 0) { append(text[i]); i++ } else {
                    withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                }
            }
            else -> { append(text[i]); i++ }
        }
    }
}
