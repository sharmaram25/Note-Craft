package com.notecraft.app.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

enum class LinePrefix { TASK, BULLET, NUMBER, QUOTE, HEADING }

object EditorOps {

    private val listMarker = Regex("""^(\s*)([-*+]\s\[[ xX]]\s|[-*+]\s|(\d+)([.)])\s|>\s?)""")
    private val anyPrefix = Regex("""^(\s*)([-*+]\s\[[ xX]]\s|[-*+]\s|\d+[.)]\s|>\s?|#{1,6}\s)?""")

    private fun lineStart(text: String, i: Int) = text.lastIndexOf('\n', (i - 1).coerceAtLeast(-1)).let { if (i == 0) 0 else it + 1 }
    private fun lineEnd(text: String, i: Int) = text.indexOf('\n', i).let { if (it < 0) text.length else it }

    /**
     * If the user just typed Enter at the end of a list item, continue the list.
     * Enter on an empty item ends the list instead.
     */
    fun onTextChange(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
        val o = old.text; val n = new.text
        if (n.length != o.length + 1 || !new.selection.collapsed) return new
        val cur = new.selection.start
        if (cur == 0 || n[cur - 1] != '\n') return new
        if (n.substring(0, cur - 1) != o.substring(0, cur - 1)) return new

        val prevStart = lineStart(n, cur - 1)
        val prevLine = n.substring(prevStart, cur - 1)
        val m = listMarker.find(prevLine) ?: return new
        val indent = m.groupValues[1]
        val marker = m.groupValues[2]
        val content = prevLine.substring(m.range.last + 1)

        if (content.isBlank()) {
            // End the list: drop the empty marker and the newline just typed.
            val t = n.substring(0, prevStart) + n.substring(cur)
            return TextFieldValue(t, TextRange(prevStart))
        }
        val next = when {
            marker.contains('[') -> "${marker.first()} [ ] "
            m.groupValues[3].isNotEmpty() -> "${m.groupValues[3].toInt() + 1}${m.groupValues[4]} "
            marker.startsWith(">") -> "> "
            else -> marker
        }
        val insert = indent + next
        val t = n.substring(0, cur) + insert + n.substring(cur)
        return TextFieldValue(t, TextRange(cur + insert.length))
    }

    /** Wraps the selection in [marker] (e.g. "**"), or unwraps if already wrapped. */
    fun toggleWrap(v: TextFieldValue, marker: String): TextFieldValue {
        val t = v.text
        val s = v.selection.min; val e = v.selection.max
        val ml = marker.length
        if (s >= ml && e + ml <= t.length && t.substring(s - ml, s) == marker && t.substring(e, e + ml) == marker) {
            val nt = t.substring(0, s - ml) + t.substring(s, e) + t.substring(e + ml)
            return TextFieldValue(nt, TextRange(s - ml, e - ml))
        }
        val sel = t.substring(s, e)
        if (sel.length >= 2 * ml && sel.startsWith(marker) && sel.endsWith(marker)) {
            val inner = sel.substring(ml, sel.length - ml)
            return TextFieldValue(t.substring(0, s) + inner + t.substring(e), TextRange(s, s + inner.length))
        }
        if (s == e) {
            // No selection: wrap the word under the cursor if any, else insert an empty pair.
            val ws = run { var i = s; while (i > 0 && t[i - 1].isLetterOrDigit()) i--; i }
            val we = run { var i = s; while (i < t.length && t[i].isLetterOrDigit()) i++; i }
            if (we > ws) {
                val nt = t.substring(0, ws) + marker + t.substring(ws, we) + marker + t.substring(we)
                return TextFieldValue(nt, TextRange(s + ml))
            }
            return TextFieldValue(t.substring(0, s) + marker + marker + t.substring(s), TextRange(s + ml))
        }
        val nt = t.substring(0, s) + marker + sel + marker + t.substring(e)
        return TextFieldValue(nt, TextRange(s + ml, e + ml))
    }

    /** Toggles a block prefix on every line touched by the selection. Headings cycle H1→H2→H3→none. */
    fun toggleLinePrefix(v: TextFieldValue, kind: LinePrefix): TextFieldValue {
        val t = v.text
        val start = lineStart(t, v.selection.min)
        val end = lineEnd(t, v.selection.max)
        val lines = t.substring(start, end).split('\n')

        val allHave = lines.filter { it.isNotBlank() }.ifEmpty { lines }.all { has(it, kind) }
        var counter = 1
        val newLines = lines.map { line ->
            val m = anyPrefix.find(line)!!
            val indent = m.groupValues[1]
            val existing = m.groupValues[2]
            val rest = line.substring(m.range.last + 1)
            if (kind == LinePrefix.HEADING) {
                val level = if (existing.startsWith("#")) existing.trim().length else 0
                val nextLevel = if (level >= 3) 0 else level + 1
                return@map indent + (if (nextLevel == 0) "" else "#".repeat(nextLevel) + " ") + rest
            }
            if (allHave) indent + rest
            else if (line.isBlank() && lines.size > 1) line
            else indent + when (kind) {
                LinePrefix.TASK -> "- [ ] "
                LinePrefix.BULLET -> "- "
                LinePrefix.NUMBER -> "${counter++}. "
                LinePrefix.QUOTE -> "> "
                LinePrefix.HEADING -> ""
            } + rest
        }
        val replaced = newLines.joinToString("\n")
        val nt = t.substring(0, start) + replaced + t.substring(end)
        val delta = replaced.length - (end - start)
        val sel = if (v.selection.collapsed) {
            val c = (v.selection.start + (newLines.first().length - lines.first().length)).coerceIn(start, start + newLines.first().length)
            TextRange(c)
        } else TextRange(start, end + delta)
        return TextFieldValue(nt, sel)
    }

    private fun has(line: String, kind: LinePrefix): Boolean {
        val l = line.trimStart()
        return when (kind) {
            LinePrefix.TASK -> Regex("""^[-*+]\s\[[ xX]]\s?""").containsMatchIn(l)
            LinePrefix.BULLET -> Regex("""^[-*+]\s(?!\[[ xX]])""").containsMatchIn(l)
            LinePrefix.NUMBER -> Regex("""^\d+[.)]\s""").containsMatchIn(l)
            LinePrefix.QUOTE -> l.startsWith(">")
            LinePrefix.HEADING -> l.startsWith("#")
        }
    }

    fun indent(v: TextFieldValue, outdent: Boolean): TextFieldValue {
        val t = v.text
        val start = lineStart(t, v.selection.min)
        val end = lineEnd(t, v.selection.max)
        val lines = t.substring(start, end).split('\n')
        var firstDelta = 0
        val newLines = lines.mapIndexed { i, line ->
            val nl = if (outdent) line.removePrefix("  ").let { if (it == line) line.removePrefix("\t") else it } else "  $line"
            if (i == 0) firstDelta = nl.length - line.length
            nl
        }
        val replaced = newLines.joinToString("\n")
        val nt = t.substring(0, start) + replaced + t.substring(end)
        val delta = replaced.length - (end - start)
        return if (v.selection.collapsed) TextFieldValue(nt, TextRange((v.selection.start + firstDelta).coerceAtLeast(start)))
        else TextFieldValue(nt, TextRange(start, end + delta))
    }

    fun insertLink(v: TextFieldValue): TextFieldValue {
        val t = v.text
        val s = v.selection.min; val e = v.selection.max
        val sel = t.substring(s, e)
        return if (sel.matches(Regex("""https?://\S+"""))) {
            val nt = t.substring(0, s) + "[](" + sel + ")" + t.substring(e)
            TextFieldValue(nt, TextRange(s + 1))
        } else {
            val label = sel.ifEmpty { "link" }
            val nt = t.substring(0, s) + "[" + label + "](https://)" + t.substring(e)
            val urlStart = s + label.length + 3
            TextFieldValue(nt, TextRange(urlStart, urlStart + 8))
        }
    }

    fun insertAtCursor(v: TextFieldValue, s: String): TextFieldValue {
        val t = v.text
        val nt = t.substring(0, v.selection.min) + s + t.substring(v.selection.max)
        return TextFieldValue(nt, TextRange(v.selection.min + s.length))
    }

    /** Inserts text on its own line at the cursor (e.g. a horizontal rule or a code fence). */
    fun insertBlock(v: TextFieldValue, block: String, cursorOffset: Int = block.length): TextFieldValue {
        val t = v.text
        val at = v.selection.min
        val needsLeadingNl = at > 0 && t[at - 1] != '\n'
        val prefix = if (needsLeadingNl) "\n" else ""
        val nt = t.substring(0, at) + prefix + block + t.substring(v.selection.max)
        return TextFieldValue(nt, TextRange(at + prefix.length + cursorOffset))
    }

    fun lineIndexAt(text: String, offset: Int): Int = text.substring(0, offset.coerceIn(0, text.length)).count { it == '\n' }
}

/** Coalescing undo/redo history for the editor body. */
class UndoHistory(private val limit: Int = 200) {
    private val undo = ArrayDeque<TextFieldValue>()
    private val redo = ArrayDeque<TextFieldValue>()
    private var lastPush = 0L
    private var lastText = ""

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    fun reset(v: TextFieldValue) { undo.clear(); redo.clear(); lastText = v.text; lastPush = 0 }

    /** Call with the value *before* a change. Groups rapid typing into word-sized steps. */
    fun record(before: TextFieldValue, after: TextFieldValue) {
        if (before.text == after.text) return
        val now = System.currentTimeMillis()
        val boundary = after.text.length - before.text.length !in -1..1 ||
            after.text.lastOrNull()?.let { it == ' ' || it == '\n' } == true ||
            now - lastPush > 1200
        if (boundary || undo.isEmpty()) {
            undo.addLast(before)
            if (undo.size > limit) undo.removeFirst()
            lastPush = now
        }
        redo.clear()
        lastText = after.text
    }

    fun undo(current: TextFieldValue): TextFieldValue? {
        val prev = undo.removeLastOrNull() ?: return null
        redo.addLast(current)
        lastPush = 0
        return prev
    }

    fun redo(current: TextFieldValue): TextFieldValue? {
        val next = redo.removeLastOrNull() ?: return null
        undo.addLast(current)
        lastPush = 0
        return next
    }
}
