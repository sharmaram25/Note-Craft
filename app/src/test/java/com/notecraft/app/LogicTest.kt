package com.notecraft.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.notecraft.app.data.NoteRepository
import com.notecraft.app.data.db.ChecklistItemEntity
import com.notecraft.app.data.db.Repeat
import com.notecraft.app.domain.NaturalDate
import com.notecraft.app.domain.TextTools
import com.notecraft.app.ui.editor.EditorOps
import com.notecraft.app.ui.editor.LinePrefix
import com.notecraft.app.ui.editor.MdBlock
import com.notecraft.app.ui.editor.UndoHistory
import com.notecraft.app.ui.editor.parseBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class NaturalDateTest {
    // Saturday 14 Nov 2026, 10:00
    private val now = LocalDateTime.of(2026, 11, 14, 10, 0)
    private fun at(ms: Long?) = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms!!), ZoneId.systemDefault())

    @Test fun tomorrowAt6pm() {
        val r = NaturalDate.parse("Call mom tomorrow at 6pm", now)
        assertEquals("Call mom", r.title)
        assertEquals(LocalDateTime.of(2026, 11, 15, 18, 0), at(r.dueAt))
    }

    @Test fun relativeMinutes() {
        val r = NaturalDate.parse("stretch in 20 min", now)
        assertEquals("Stretch", r.title)
        assertEquals(now.plusMinutes(20), at(r.dueAt))
    }

    @Test fun everyWeekdayWithTime() {
        val r = NaturalDate.parse("Standup every weekday 9:30", now)
        assertEquals("Standup", r.title)
        assertEquals(Repeat.WEEKDAYS, r.repeat)
        assertEquals(9, at(r.dueAt).hour); assertEquals(30, at(r.dueAt).minute)
    }

    @Test fun dayMonthDate() {
        val r = NaturalDate.parse("Pay rent on 1 dec", now)
        assertEquals("Pay rent", r.title)
        assertEquals(LocalDateTime.of(2026, 12, 1, 9, 0), at(r.dueAt))
    }

    @Test fun pastTimeTodayRollsToTomorrow() {
        val r = NaturalDate.parse("Water plants at 8am", now)
        assertEquals(LocalDateTime.of(2026, 11, 15, 8, 0), at(r.dueAt))
    }

    @Test fun weekdayName() {
        val r = NaturalDate.parse("Dentist friday 4pm", now)
        assertEquals("Dentist", r.title)
        assertEquals(DayOfWeek.FRIDAY, at(r.dueAt).dayOfWeek)
        assertEquals(16, at(r.dueAt).hour)
    }

    @Test fun tonight() {
        val r = NaturalDate.parse("Take out trash tonight", now)
        assertEquals("Take out trash", r.title)
        assertEquals(LocalDateTime.of(2026, 11, 14, 20, 0), at(r.dueAt))
    }

    @Test fun plainTextHasNoDate() {
        val r = NaturalDate.parse("Buy milk", now)
        assertEquals("Buy milk", r.title)
        assertNull(r.dueAt)
        assertEquals(Repeat.NONE, r.repeat)
    }

    @Test fun nextOccurrenceSkipsWeekend() {
        val zone = ZoneId.systemDefault()
        val fri = LocalDateTime.of(2026, 11, 13, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val next = NoteRepository.nextOccurrence(fri, Repeat.WEEKDAYS, fri)
        assertEquals(DayOfWeek.MONDAY, LocalDateTime.ofInstant(Instant.ofEpochMilli(next), zone).dayOfWeek)
    }
}

class EditorOpsTest {
    private fun typeEnter(text: String): TextFieldValue {
        val old = TextFieldValue(text, TextRange(text.length))
        val new = TextFieldValue(text + "\n", TextRange(text.length + 1))
        return EditorOps.onTextChange(old, new)
    }

    @Test fun continuesBullets() = assertEquals("- one\n- ", typeEnter("- one").text)
    @Test fun continuesTasksUnchecked() = assertEquals("- [x] done\n- [ ] ", typeEnter("- [x] done").text)
    @Test fun incrementsNumbers() = assertEquals("1. a\n2. ", typeEnter("1. a").text)
    @Test fun keepsIndent() = assertEquals("  - a\n  - ", typeEnter("  - a").text)
    @Test fun emptyItemEndsList() {
        val r = typeEnter("- a\n- ")
        assertEquals("- a\n", r.text)
        assertEquals(4, r.selection.start)
    }
    @Test fun plainLineUnchanged() = assertEquals("hello\n", typeEnter("hello").text)

    @Test fun wrapAndUnwrapSelection() {
        val v = TextFieldValue("make bold now", TextRange(5, 9))
        val wrapped = EditorOps.toggleWrap(v, "**")
        assertEquals("make **bold** now", wrapped.text)
        assertEquals(TextRange(7, 11), wrapped.selection)
        assertEquals("make bold now", EditorOps.toggleWrap(wrapped, "**").text)
    }

    @Test fun wrapWordUnderCursor() {
        val v = TextFieldValue("say hello", TextRange(6))
        assertEquals("say *hello*", EditorOps.toggleWrap(v, "*").text)
    }

    @Test fun toggleTaskPrefixOnMultipleLines() {
        val v = TextFieldValue("milk\neggs", TextRange(0, 9))
        val r = EditorOps.toggleLinePrefix(v, LinePrefix.TASK)
        assertEquals("- [ ] milk\n- [ ] eggs", r.text)
        assertEquals("milk\neggs", EditorOps.toggleLinePrefix(r.copy(selection = TextRange(0, r.text.length)), LinePrefix.TASK).text)
    }

    @Test fun headingCycles() {
        var v = TextFieldValue("Title", TextRange(2))
        v = EditorOps.toggleLinePrefix(v, LinePrefix.HEADING); assertEquals("# Title", v.text)
        v = EditorOps.toggleLinePrefix(v, LinePrefix.HEADING); assertEquals("## Title", v.text)
        v = EditorOps.toggleLinePrefix(v, LinePrefix.HEADING); assertEquals("### Title", v.text)
        v = EditorOps.toggleLinePrefix(v, LinePrefix.HEADING); assertEquals("Title", v.text)
    }

    @Test fun indentAndOutdent() {
        val v = TextFieldValue("- a", TextRange(3))
        val i = EditorOps.indent(v, false)
        assertEquals("  - a", i.text)
        assertEquals("- a", EditorOps.indent(i, true).text)
    }

    @Test fun undoRedo() {
        val h = UndoHistory()
        val a = TextFieldValue("a"); val b = TextFieldValue("a b"); val c = TextFieldValue("a b c")
        h.reset(a); h.record(a, b); h.record(b, c)
        val u = h.undo(c)
        assertNotNull(u)
        assertTrue(h.canRedo)
        assertEquals(c.text, h.redo(u!!)!!.text)
    }
}

class TextToolsTest {
    @Test fun hashtagsIgnoreHeadingsAndCode() {
        val body = "# Heading\nSome #work and #ideas/later\n```\n#notatag\n```\n#123 #work"
        assertEquals(listOf("work", "ideas/later"), TextTools.hashtags(body))
    }

    @Test fun previewStripsMarkdown() {
        val p = TextTools.plainPreview("# Title\n**bold** and *it* [link](http://x)\n- [ ] task")
        assertEquals("Title\nbold and it link\n☐ task", p)
    }

    @Test fun toggleTask() {
        assertEquals("- [x] a\n- [ ] b", TextTools.toggleTaskAtLine("- [ ] a\n- [ ] b", 0))
        assertEquals("- [ ] a", TextTools.toggleTaskAtLine("- [x] a", 0))
    }

    @Test fun checklistBodyUncheckedFirst() {
        val items = listOf(
            ChecklistItemEntity(1, 1, "done", checked = true, position = 0),
            ChecklistItemEntity(2, 1, "todo", checked = false, position = 1),
        )
        assertEquals("- [ ] todo\n- [x] done", TextTools.checklistBody(items))
    }

    @Test fun splitPastedLines() =
        assertEquals(listOf("Milk", "Eggs", "Bread"), TextTools.splitToItems("- Milk\n• Eggs\n\n1. Bread"))

    @Test fun legacyHtmlToMarkdown() {
        val html = "<div><b>Hello</b> <i>world</i></div><ul><li>one</li><li>two</li></ul><ol><li>first</li></ol><h1>Big</h1>a&amp;b&nbsp;c<br>next"
        val md = TextTools.htmlToMarkdown(html)
        assertEquals("**Hello** *world*\n\n- one\n- two\n\n1. first\n\n# Big\na&b c\nnext", md)
        assertTrue(TextTools.looksLikeHtml(html))
        assertFalse(TextTools.looksLikeHtml("plain **markdown**"))
    }

    @Test fun wordCount() = assertEquals(4, TextTools.wordCount("It's a **fine** day!"))
}

class MarkdownParseTest {
    @Test fun blocks() {
        val blocks = parseBlocks("# H\n\npara line\n- [x] task\n- bullet\n2. num\n> quote\n```\ncode\n```\n---")
        assertEquals(MdBlock.Heading(1, "H"), blocks[0])
        assertEquals(MdBlock.Gap, blocks[1])
        assertEquals(MdBlock.Paragraph("para line"), blocks[2])
        assertEquals(MdBlock.Task(0, true, "task", 3), blocks[3])
        assertEquals(MdBlock.Bullet(0, "bullet"), blocks[4])
        assertEquals(MdBlock.Ordered(0, "2.", "num"), blocks[5])
        assertEquals(MdBlock.Quote("quote"), blocks[6])
        assertEquals(MdBlock.Code("code"), blocks[7])
        assertEquals(MdBlock.Rule, blocks[8])
    }
}
