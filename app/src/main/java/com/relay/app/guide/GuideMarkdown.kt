package com.relay.app.guide

/** One block of the usage guide, as the guide screen draws it. */
sealed interface GuideBlock {
    data class Heading(val level: Int, val text: String) : GuideBlock
    data class Paragraph(val text: String) : GuideBlock
    data class Bullets(val items: List<String>) : GuideBlock
    data class Code(val text: String) : GuideBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : GuideBlock
    data object Rule : GuideBlock
}

/**
 * Just enough Markdown for `docs/usage-guide*.md`: headings, paragraphs, bullet lists (with
 * indented continuation lines), fenced code, pipe tables and `---` rules. Inline `**bold**` and
 * `code` stay in the text for the screen to style. Pure, unit-tested; no library needed.
 */
object GuideMarkdown {
    fun parse(text: String): List<GuideBlock> {
        val lines = text.replace("\r\n", "\n").split("\n")
        val out = mutableListOf<GuideBlock>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> i++
                trimmed.startsWith("```") -> {
                    val code = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) code += lines[i++]
                    i++ // closing fence
                    out += GuideBlock.Code(code.joinToString("\n"))
                }
                trimmed.startsWith("#") -> {
                    val level = trimmed.takeWhile { it == '#' }.length
                    out += GuideBlock.Heading(level, trimmed.drop(level).trim())
                    i++
                }
                trimmed == "---" -> { out += GuideBlock.Rule; i++ }
                trimmed.startsWith("|") -> {
                    val rows = mutableListOf<List<String>>()
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        val cells = lines[i].trim().trim('|').split("|").map { it.trim() }
                        // Skip the |---|---| separator row.
                        if (!cells.all { c -> c.isNotEmpty() && c.all { it == '-' || it == ':' } }) rows += cells
                        i++
                    }
                    if (rows.isNotEmpty()) out += GuideBlock.Table(rows.first(), rows.drop(1))
                }
                trimmed.startsWith("- ") -> {
                    val items = mutableListOf<String>()
                    while (i < lines.size) {
                        val l = lines[i]
                        when {
                            l.trim().startsWith("- ") -> { items += l.trim().removePrefix("- ").trim(); i++ }
                            l.startsWith("  ") && l.isNotBlank() && items.isNotEmpty() -> {
                                items[items.lastIndex] = items.last() + " " + l.trim(); i++
                            }
                            else -> break
                        }
                    }
                    out += GuideBlock.Bullets(items)
                }
                else -> {
                    val para = mutableListOf<String>()
                    while (i < lines.size) {
                        val t = lines[i].trim()
                        if (t.isEmpty() || t.startsWith("#") || t.startsWith("- ") || t.startsWith("|") || t.startsWith("```") || t == "---") break
                        para += t
                        i++
                    }
                    out += GuideBlock.Paragraph(para.joinToString(" "))
                }
            }
        }
        return out
    }

    /**
     * Index of the block where pane section [n] starts (0 = the first `##` heading). Sections are
     * found by position, not by text, so every translation keeps the same order of `##` sections.
     */
    fun sectionStart(blocks: List<GuideBlock>, n: Int): Int? =
        blocks.withIndex().filter { (_, b) -> b is GuideBlock.Heading && b.level == 2 }.getOrNull(n)?.index
}

/** The guide's `##` sections, in order. Must match the order in both guide files. */
enum class GuideSection { ALARMS, TIMERS, RELAYS, STOPWATCH, CLOCK, WIDGETS, SETTINGS }
