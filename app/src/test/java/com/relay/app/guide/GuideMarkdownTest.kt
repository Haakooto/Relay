package com.relay.app.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GuideMarkdownTest {
    @Test fun parsesTheBlocksTheGuideUses() {
        val md = """
            # Title

            Intro line one
            continues here.

            ---

            ## Alarms

            - **Label**: shown when it rings.
            - Repeat: tick weekdays,
              or none for a one-off.

            | You write | Means |
            |---|---|
            | `25m` | A step. |

            ```
            (Work, Break)
            ```
        """.trimIndent()
        val blocks = GuideMarkdown.parse(md)
        assertEquals(GuideBlock.Heading(1, "Title"), blocks[0])
        assertEquals(GuideBlock.Paragraph("Intro line one continues here."), blocks[1])
        assertEquals(GuideBlock.Rule, blocks[2])
        assertEquals(GuideBlock.Heading(2, "Alarms"), blocks[3])
        assertEquals(GuideBlock.Bullets(listOf("**Label**: shown when it rings.", "Repeat: tick weekdays, or none for a one-off.")), blocks[4])
        assertEquals(GuideBlock.Table(listOf("You write", "Means"), listOf(listOf("`25m`", "A step."))), blocks[5])
        assertEquals(GuideBlock.Code("(Work, Break)"), blocks[6])
        assertEquals(3, GuideMarkdown.sectionStart(blocks, 0))
    }

    /** Both guides must have one `##` section per pane, in the same order, for the ? buttons. */
    @Test fun bothGuidesHaveEverySection() {
        for (name in listOf("usage-guide.md", "usage-guide.nn.md")) {
            val file = File("../docs/$name")
            assertTrue("$name missing", file.exists())
            val sections = GuideMarkdown.parse(file.readText()).count { it is GuideBlock.Heading && it.level == 2 }
            assertEquals(name, GuideSection.entries.size, sections)
        }
    }
}
