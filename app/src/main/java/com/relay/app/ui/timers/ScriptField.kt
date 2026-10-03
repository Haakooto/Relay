package com.relay.app.ui.timers

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.relay.app.library.EditState
import com.relay.app.library.ScriptEditing
import com.relay.app.library.ScriptRefs

/**
 * The relay script editor (spec §05): monospace, highlighted, with auto-close / auto-indent
 * ([ScriptEditing]) and a suggestion row for names and functions matching the word being typed.
 */
@Composable
fun ScriptField(
    script: String,
    onScriptChange: (String) -> Unit,
    candidates: List<String>,
    isError: Boolean,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by remember { mutableStateOf(TextFieldValue(script, TextRange(script.length))) }
    // Follow outside changes (initial load) without fighting the cursor while typing.
    LaunchedEffect(script) { if (script != value.text) value = TextFieldValue(script, TextRange(script.length)) }

    val colors = MaterialTheme.colorScheme
    val highlighter = remember(candidates, colors) {
        ScriptHighlighter(candidates.toSet(), colors.primary, colors.secondary, colors.tertiary, colors.error, colors.onSurfaceVariant)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { new ->
                val out = ScriptEditing.transform(
                    EditState(value.text, value.selection.start, value.selection.end),
                    EditState(new.text, new.selection.start, new.selection.end),
                )
                value = if (out.text == new.text && out.selectionStart == new.selection.start) new
                else TextFieldValue(out.text, TextRange(out.selectionStart, out.selectionEnd))
                onScriptChange(value.text)
            },
            label = label,
            isError = isError,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace),
            visualTransformation = highlighter,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            minLines = 5,
            modifier = Modifier.fillMaxWidth(),
        )
        val word = if (value.selection.collapsed) ScriptRefs.wordAt(value.text, value.selection.start) else null
        val suggestions = word?.let { (_, prefix) ->
            (candidates + FUNCTIONS.keys).filter { it.startsWith(prefix, ignoreCase = true) && it != prefix }.take(8)
        }.orEmpty()
        if (suggestions.isNotEmpty() && word != null) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { suggestion ->
                    SuggestionChip(
                        onClick = {
                            val insert = FUNCTIONS[suggestion] ?: suggestion
                            val out = ScriptEditing.complete(EditState(value.text, value.selection.start), word.first, insert)
                            // Functions land with the cursor inside their parentheses.
                            val cursor = if (suggestion in FUNCTIONS) out.selectionStart - 1 else out.selectionStart
                            value = TextFieldValue(out.text, TextRange(cursor))
                            onScriptChange(value.text)
                        },
                        label = { Text(suggestion) },
                    )
                }
            }
        }
    }
}

private val FUNCTIONS = mapOf("repeat" to "repeat()", "range" to "range()")

/** Colours numbers, names, functions and labels. Names not in the library show as errors. */
private class ScriptHighlighter(
    private val known: Set<String>,
    private val numberColor: Color,
    private val functionColor: Color,
    private val labelColor: Color,
    private val unknownColor: Color,
    private val commentColor: Color,
) : VisualTransformation {
    private val token = Regex("\"[^\"\\n]*\"?|#[^\\n]*|\\d[\\dhms.]*|[\\p{L}][\\p{L}\\d_]*")
    private val functions = setOf("repeat", "range", "RANDOM", "MINUTE", "HOUR", "SECOND", "NOW")

    override fun filter(text: AnnotatedString): TransformedText {
        val styled = buildAnnotatedString {
            append(text.text)
            for (m in token.findAll(text.text)) {
                val t = m.value
                val style = when {
                    t.startsWith("#") -> SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)
                    t.startsWith("\"") -> SpanStyle(color = labelColor)
                    t[0].isDigit() -> SpanStyle(color = numberColor)
                    t in functions -> SpanStyle(color = functionColor)
                    t in known -> SpanStyle(fontWeight = FontWeight.Bold)
                    else -> SpanStyle(color = unknownColor)
                }
                addStyle(style, m.range.first, m.range.last + 1)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    override fun equals(other: Any?) = other is ScriptHighlighter && other.known == known
    override fun hashCode() = known.hashCode()
}
