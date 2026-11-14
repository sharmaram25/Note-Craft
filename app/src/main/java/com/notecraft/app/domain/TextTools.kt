package com.notecraft.app.domain

import com.notecraft.app.data.db.ChecklistItemEntity

object TextTools {

    private val hashtagRegex = Regex("""(?<=^|[\s(\[,;])#([\p{L}\p{N}_][\p{L}\p{N}_/\-]*)""")
    private val wordRegex = Regex("""[\p{L}\p{N}']+""")
    private val checkboxLine = Regex("""^(\s*)[-*+]\s\[([ xX])]\s?(.*)$""")
    private val fence = Regex("""^\s*```""")

    /** Hashtags found in [body], without '#', in order of first appearance, case-preserving. */
    fun hashtags(body: String): List<String> {
        if ('#' !in body) return emptyList()
        val out = LinkedHashMap<String, String>()
        var inCode = false
        for (line in body.lineSequence()) {
            if (fence.containsMatchIn(line)) { inCode = !inCode; continue }
            if (inCode || '#' !in line) continue
            for (m in hashtagRegex.findAll(line)) {
                val tag = m.groupValues[1].trimEnd('/', '-')
                if (tag.isEmpty() || tag.none { it.isLetter() }) continue
                out.putIfAbsent(tag.lowercase(), tag)
            }
        }
        return out.values.toList()
    }

    fun hashtagRanges(line: String): Sequence<IntRange> =
        hashtagRegex.findAll(line).filter { m -> m.groupValues[1].any { it.isLetter() } }.map { it.range }

    fun wordCount(text: String): Int = wordRegex.findAll(text).count()

    fun readingMinutes(words: Int): Int = maxOf(1, (words + 229) / 230)

    /** Strips markdown syntax for compact card / widget / notification previews. */
    fun plainPreview(body: String, maxChars: Int = 400): String {
        if (body.isBlank()) return ""
        val sb = StringBuilder()
        for (raw in body.lineSequence()) {
            if (sb.length > maxChars) break
            var line = raw.trim()
            if (line.isEmpty()) { if (sb.isNotEmpty() && sb.last() != '\n') sb.append('\n'); continue }
            if (line.startsWith("```") || line.matches(Regex("""^([-*_])\1{2,}$"""))) continue
            val cb = checkboxLine.find(line)
            line = if (cb != null) {
                (if (cb.groupValues[2].isBlank()) "☐ " else "☑ ") + cb.groupValues[3]
            } else {
                line.replace(Regex("""^#{1,6}\s+"""), "")
                    .replace(Regex("""^>\s?"""), "")
                    .replace(Regex("""^[-*+]\s+"""), "• ")
            }
            line = inlineStrip(line)
            sb.append(line).append('\n')
        }
        return sb.toString().trim().take(maxChars)
    }

    fun inlineStrip(s: String): String = s
        .replace(Regex("""!\[[^\]]*]\([^)]*\)"""), "")
        .replace(Regex("""\[\[([^\]|]+)(?:\|([^\]]+))?]]""")) { it.groupValues[2].ifEmpty { it.groupValues[1] } }
        .replace(Regex("""\[([^\]]+)]\([^)]+\)"""), "$1")
        .replace(Regex("""(\*\*|__)(.+?)\1"""), "$2")
        .replace(Regex("""(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?!\*)"""), "$1")
        .replace(Regex("""(?<![\w_])_(?!\s)(.+?)(?<!\s)_(?![\w_])"""), "$1")
        .replace(Regex("""~~(.+?)~~"""), "$1")
        .replace(Regex("""==(.+?)=="""), "$1")
        .replace(Regex("""`([^`]+)`"""), "$1")

    // ---- Checklists ---------------------------------------------------------------------

    /** Body text mirrored onto checklist notes: unchecked first, as markdown task lines. */
    fun checklistBody(items: List<ChecklistItemEntity>): String {
        val sorted = items.sortedWith(compareBy({ it.checked }, { it.position }))
        return sorted.filter { it.text.isNotBlank() }
            .joinToString("\n") { "- [${if (it.checked) "x" else " "}] ${it.text.trim()}" }
    }

    data class TaskLine(val checked: Boolean, val text: String)

    fun parseTasks(body: String): List<TaskLine> = body.lineSequence().mapNotNull { line ->
        checkboxLine.find(line)?.let { TaskLine(it.groupValues[2].isNotBlank(), it.groupValues[3]) }
    }.toList()

    fun isTaskLine(line: String) = checkboxLine.matches(line)

    /** Toggles the markdown checkbox on the given 0-based line index. */
    fun toggleTaskAtLine(body: String, lineIndex: Int): String {
        val lines = body.split('\n').toMutableList()
        if (lineIndex !in lines.indices) return body
        val m = checkboxLine.find(lines[lineIndex]) ?: return body
        val checked = m.groupValues[2].isNotBlank()
        val line = lines[lineIndex]
        val idx = line.indexOf('[')
        lines[lineIndex] = line.substring(0, idx + 1) + (if (checked) " " else "x") + line.substring(idx + 2)
        return lines.joinToString("\n")
    }

    fun taskProgress(body: String): Pair<Int, Int> {
        var done = 0; var total = 0
        for (line in body.lineSequence()) {
            val m = checkboxLine.find(line) ?: continue
            total++
            if (m.groupValues[2].isNotBlank()) done++
        }
        return done to total
    }

    /** Converts free text (e.g. pasted lines) into checklist item strings. */
    fun splitToItems(text: String): List<String> = text.lines()
        .map { it.trim().replace(Regex("""^([-*+•☐☑]|\d+[.)])\s*(\[[ xX]]\s*)?"""), "").trim() }
        .filter { it.isNotEmpty() }

    fun safeFileName(title: String, fallback: String): String {
        val cleaned = title.trim().replace(Regex("""[\\/:*?"<>|\n\r\t]"""), " ")
            .replace(Regex("""\s+"""), " ").trim().take(80)
        return cleaned.ifEmpty { fallback }
    }

    // ---- Legacy HTML (from the old WebView rich editor) -> Markdown -----------------------

    fun looksLikeHtml(s: String) = Regex("""<\s*(div|p|br|b|strong|i|em|u|h[1-6]|ul|ol|li|span|strike|s)\b""", RegexOption.IGNORE_CASE).containsMatchIn(s)

    fun htmlToMarkdown(html: String): String {
        var s = html.replace("\r", "")
        val ic = RegexOption.IGNORE_CASE
        s = s.replace(Regex("""<\s*br\s*/?>""", ic), "\n")
        s = s.replace(Regex("""<\s*h([1-6])[^>]*>(.*?)<\s*/\s*h\1\s*>""", setOf(ic, RegexOption.DOT_MATCHES_ALL))) {
            "\n" + "#".repeat(it.groupValues[1].toInt()) + " " + it.groupValues[2].trim() + "\n"
        }
        s = s.replace(Regex("""<\s*(b|strong)\b[^>]*>(.*?)<\s*/\s*\1\s*>""", setOf(ic, RegexOption.DOT_MATCHES_ALL))) { wrapInline(it.groupValues[2], "**") }
        s = s.replace(Regex("""<\s*(i|em)\b[^>]*>(.*?)<\s*/\s*\1\s*>""", setOf(ic, RegexOption.DOT_MATCHES_ALL))) { wrapInline(it.groupValues[2], "*") }
        s = s.replace(Regex("""<\s*(s|strike|del)\b[^>]*>(.*?)<\s*/\s*\1\s*>""", setOf(ic, RegexOption.DOT_MATCHES_ALL))) { wrapInline(it.groupValues[2], "~~") }
        s = s.replace(Regex("""<\s*u\b[^>]*>(.*?)<\s*/\s*u\s*>""", setOf(ic, RegexOption.DOT_MATCHES_ALL)), "$1")
        s = s.replace(Regex("""<\s*a\b[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)<\s*/\s*a\s*>""", setOf(ic, RegexOption.DOT_MATCHES_ALL)), "[$2]($1)")
        // Ordered lists: number items sequentially.
        s = s.replace(Regex("""<\s*ol\b[^>]*>(.*?)<\s*/\s*ol\s*>""", setOf(ic, RegexOption.DOT_MATCHES_ALL))) { m ->
            var n = 0
            "\n" + m.groupValues[1].replace(Regex("""<\s*li\b[^>]*>(.*?)<\s*/\s*li\s*>""", setOf(ic, RegexOption.DOT_MATCHES_ALL))) {
                n++; "$n. ${it.groupValues[1].trim()}\n"
            } + "\n"
        }
        s = s.replace(Regex("""<\s*li\b[^>]*>(.*?)<\s*/\s*li\s*>""", setOf(ic, RegexOption.DOT_MATCHES_ALL))) { "- ${it.groupValues[1].trim()}\n" }
        s = s.replace(Regex("""<\s*/?\s*(ul|ol)\b[^>]*>""", ic), "\n")
        s = s.replace(Regex("""<\s*/\s*(div|p)\s*>""", ic), "\n")
        s = s.replace(Regex("""<\s*(div|p)\b[^>]*>""", ic), "\n")
        s = s.replace(Regex("""<[^>]+>"""), "")
        s = decodeEntities(s)
        s = s.lines().joinToString("\n") { it.trimEnd() }
        s = s.replace(Regex("""\n{3,}"""), "\n\n")
        return s.trim()
    }

    private fun wrapInline(inner: String, marker: String): String {
        val t = inner.trim()
        if (t.isEmpty()) return inner
        val lead = inner.takeWhile { it.isWhitespace() }
        val trail = inner.takeLastWhile { it.isWhitespace() }
        return "$lead$marker$t$marker$trail"
    }

    private fun decodeEntities(s: String): String = s
        .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace(Regex("""&#(\d+);""")) { it.groupValues[1].toIntOrNull()?.let { c -> String(Character.toChars(c)) } ?: it.value }
        .replace("&amp;", "&")
}
