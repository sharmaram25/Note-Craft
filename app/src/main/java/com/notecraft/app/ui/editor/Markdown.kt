package com.notecraft.app.ui.editor

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.notecraft.app.domain.TextTools

@Immutable
data class MdPalette(
    val text: Color,
    val syntax: Color,
    val accent: Color,
    val muted: Color,
    val codeBg: Color,
    val highlight: Color,
    val baseSize: TextUnit,
)

private val headingRx = Regex("""^(#{1,6})\s+""")
private val quoteRx = Regex("""^(\s*>\s?)""")
private val taskRx = Regex("""^(\s*[-*+]\s\[([ xX])]\s?)""")
private val bulletRx = Regex("""^(\s*[-*+]\s)""")
private val orderedRx = Regex("""^(\s*\d+[.)]\s)""")
private val ruleRx = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
private val fenceRx = Regex("""^\s*```""")

private val codeSpanRx = Regex("""`[^`\n]+`""")
private val boldRx = Regex("""(\*\*|__)(?=\S)(.+?)(?<=\S)\1""")
private val italicStarRx = Regex("""(?<![*\w])\*(?=[^\s*])(.+?)(?<=[^\s*])\*(?!\*)""")
private val italicUnderRx = Regex("""(?<![\w_])_(?=[^\s_])(.+?)(?<=[^\s_])_(?![\w_])""")
private val strikeRx = Regex("""~~(?=\S)(.+?)(?<=\S)~~""")
private val highlightRx = Regex("""==(?=\S)(.+?)(?<=\S)==""")
private val linkRx = Regex("""\[([^\]\n]+)]\(([^)\s]+)\)""")
private val wikiRx = Regex("""\[\[([^\]\n|]+)(?:\|([^\]\n]+))?]]""")
private val urlRx = Regex("""(?<![\w(\[])(https?://[^\s<>()\[\]]+[^\s<>()\[\].,;:!?'"])""")

private fun headingScale(level: Int) = when (level) { 1 -> 1.55f; 2 -> 1.32f; 3 -> 1.16f; else -> 1.05f }

/**
 * Live-preview styling for the editor: markdown stays as plain text (portable, searchable),
 * but renders with real weights, sizes and colors while syntax characters recede.
 */
class MarkdownVisualTransformation(private val p: MdPalette) : VisualTransformation {
    private var lastText: String? = null
    private var lastResult: AnnotatedString? = null

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val styled = if (raw == lastText && lastResult != null) lastResult!! else styleEditor(raw, p).also { lastText = raw; lastResult = it }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    override fun equals(other: Any?) = other is MarkdownVisualTransformation && other.p == p
    override fun hashCode() = p.hashCode()
}

fun styleEditor(text: String, p: MdPalette): AnnotatedString {
    if (text.length > 120_000) return AnnotatedString(text)
    val b = AnnotatedString.Builder(text)
    val syntax = SpanStyle(color = p.syntax)
    var offset = 0
    var inFence = false
    val lines = text.split('\n')
    for (line in lines) {
        val s = offset
        val e = offset + line.length
        when {
            fenceRx.containsMatchIn(line) -> {
                inFence = !inFence
                b.addStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = p.syntax, background = p.codeBg), s, e)
            }
            inFence -> b.addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = p.codeBg, fontSize = p.baseSize * 0.92f), s, e)
            else -> {
                var contentStart = 0
                headingRx.find(line)?.let { m ->
                    val level = m.groupValues[1].length
                    val size = p.baseSize * headingScale(level)
                    b.addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = size, letterSpacing = (-0.2).sp), s, e)
                    b.addStyle(ParagraphStyle(lineHeight = size * 1.35f), s, if (e < text.length) e + 1 else e)
                    b.addStyle(syntax, s, s + m.range.last + 1)
                    contentStart = m.range.last + 1
                } ?: taskRx.find(line)?.let { m ->
                    val marker = m.groupValues[1]
                    b.addStyle(SpanStyle(color = p.accent, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace), s, s + marker.trimEnd().length)
                    if (m.groupValues[2].isNotBlank()) b.addStyle(SpanStyle(color = p.muted, textDecoration = TextDecoration.LineThrough), s + marker.length, e)
                    contentStart = marker.length
                } ?: bulletRx.find(line)?.let { m ->
                    b.addStyle(SpanStyle(color = p.accent, fontWeight = FontWeight.Bold), s, s + m.groupValues[1].length)
                    contentStart = m.groupValues[1].length
                } ?: orderedRx.find(line)?.let { m ->
                    b.addStyle(SpanStyle(color = p.accent, fontWeight = FontWeight.SemiBold), s, s + m.groupValues[1].length)
                    contentStart = m.groupValues[1].length
                } ?: quoteRx.find(line)?.let { m ->
                    b.addStyle(SpanStyle(color = p.accent, fontWeight = FontWeight.Bold), s, s + m.groupValues[1].length)
                    b.addStyle(SpanStyle(color = p.muted, fontStyle = FontStyle.Italic), s + m.groupValues[1].length, e)
                    contentStart = m.groupValues[1].length
                }
                if (ruleRx.matches(line)) b.addStyle(SpanStyle(color = p.syntax, letterSpacing = 4.sp), s, e)
                else if (contentStart < line.length) styleInlineEditor(b, line, s, contentStart, p)
            }
        }
        offset = e + 1
    }
    return b.toAnnotatedString()
}

private fun styleInlineEditor(b: AnnotatedString.Builder, line: String, base: Int, from: Int, p: MdPalette) {
    val syntax = SpanStyle(color = p.syntax)
    val code = codeSpanRx.findAll(line, from).map { it.range }.toList()
    fun inCode(i: Int) = code.any { i in it }

    code.forEach { r ->
        b.addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = p.codeBg, fontSize = p.baseSize * 0.92f), base + r.first, base + r.last + 1)
        b.addStyle(syntax, base + r.first, base + r.first + 1)
        b.addStyle(syntax, base + r.last, base + r.last + 1)
    }
    fun wrapped(rx: Regex, markerLen: (MatchResult) -> Int, style: SpanStyle) {
        for (m in rx.findAll(line, from)) {
            if (inCode(m.range.first)) continue
            val ml = markerLen(m)
            val st = base + m.range.first; val en = base + m.range.last + 1
            b.addStyle(style, st + ml, en - ml)
            b.addStyle(syntax, st, st + ml)
            b.addStyle(syntax, en - ml, en)
        }
    }
    wrapped(boldRx, { it.groupValues[1].length }, SpanStyle(fontWeight = FontWeight.Bold))
    wrapped(italicStarRx, { 1 }, SpanStyle(fontStyle = FontStyle.Italic))
    wrapped(italicUnderRx, { 1 }, SpanStyle(fontStyle = FontStyle.Italic))
    wrapped(strikeRx, { 2 }, SpanStyle(textDecoration = TextDecoration.LineThrough, color = p.muted))
    wrapped(highlightRx, { 2 }, SpanStyle(background = p.highlight))

    for (m in wikiRx.findAll(line, from)) {
        if (inCode(m.range.first)) continue
        val st = base + m.range.first; val en = base + m.range.last + 1
        b.addStyle(SpanStyle(color = p.accent, fontWeight = FontWeight.Medium), st + 2, en - 2)
        b.addStyle(syntax, st, st + 2); b.addStyle(syntax, en - 2, en)
    }
    for (m in linkRx.findAll(line, from)) {
        if (inCode(m.range.first)) continue
        val st = base + m.range.first
        val textEnd = st + 1 + m.groupValues[1].length
        b.addStyle(SpanStyle(color = p.accent, textDecoration = TextDecoration.Underline), st + 1, textEnd)
        b.addStyle(syntax, st, st + 1)
        b.addStyle(SpanStyle(color = p.syntax, fontSize = p.baseSize * 0.85f), textEnd, base + m.range.last + 1)
    }
    for (m in urlRx.findAll(line, from)) {
        if (inCode(m.range.first)) continue
        b.addStyle(SpanStyle(color = p.accent, textDecoration = TextDecoration.Underline), base + m.range.first, base + m.range.last + 1)
    }
    for (r in TextTools.hashtagRanges(line)) {
        if (r.first < from || inCode(r.first)) continue
        b.addStyle(SpanStyle(color = p.accent, fontWeight = FontWeight.Medium), base + r.first, base + r.last + 1)
    }
}

// ---------------------------------------------------------------------------------------
// Reading mode: parsed blocks with syntax hidden.
// ---------------------------------------------------------------------------------------

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val indent: Int, val text: String) : MdBlock
    data class Ordered(val indent: Int, val marker: String, val text: String) : MdBlock
    data class Task(val indent: Int, val checked: Boolean, val text: String, val line: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data object Rule : MdBlock
    data object Gap : MdBlock
}

fun parseBlocks(body: String): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    val lines = body.split('\n')
    var i = 0
    val para = StringBuilder()
    fun flush() { if (para.isNotEmpty()) { out += MdBlock.Paragraph(para.toString()); para.clear() } }
    fun indentOf(s: String) = s.takeWhile { it == ' ' || it == '\t' }.fold(0) { acc, ch -> acc + if (ch == '\t') 4 else 1 } / 2
    while (i < lines.size) {
        val line = lines[i]
        when {
            fenceRx.containsMatchIn(line) -> {
                flush()
                val sb = StringBuilder()
                i++
                while (i < lines.size && !fenceRx.containsMatchIn(lines[i])) { if (sb.isNotEmpty()) sb.append('\n'); sb.append(lines[i]); i++ }
                out += MdBlock.Code(sb.toString())
            }
            line.isBlank() -> { flush(); if (out.lastOrNull() !is MdBlock.Gap) out += MdBlock.Gap }
            headingRx.containsMatchIn(line) -> { flush(); val m = headingRx.find(line)!!; out += MdBlock.Heading(m.groupValues[1].length, line.substring(m.range.last + 1)) }
            ruleRx.matches(line) -> { flush(); out += MdBlock.Rule }
            taskRx.containsMatchIn(line) -> { flush(); val m = taskRx.find(line)!!; out += MdBlock.Task(indentOf(line), m.groupValues[2].isNotBlank(), line.substring(m.groupValues[1].length), i) }
            bulletRx.containsMatchIn(line) -> { flush(); val m = bulletRx.find(line)!!; out += MdBlock.Bullet(indentOf(line), line.substring(m.groupValues[1].length)) }
            orderedRx.containsMatchIn(line) -> { flush(); val m = orderedRx.find(line)!!; out += MdBlock.Ordered(indentOf(line), m.groupValues[1].trim(), line.substring(m.groupValues[1].length)) }
            quoteRx.containsMatchIn(line) -> {
                flush()
                val sb = StringBuilder(line.replaceFirst(quoteRx, ""))
                while (i + 1 < lines.size && quoteRx.containsMatchIn(lines[i + 1])) { i++; sb.append('\n').append(lines[i].replaceFirst(quoteRx, "")) }
                out += MdBlock.Quote(sb.toString())
            }
            else -> { if (para.isNotEmpty()) para.append('\n'); para.append(line) }
        }
        i++
    }
    flush()
    while (out.lastOrNull() is MdBlock.Gap) out.removeAt(out.lastIndex)
    while (out.firstOrNull() is MdBlock.Gap) out.removeAt(0)
    return out
}

/** Builds display text with markers removed and links/wiki-links/tags made tappable. */
fun renderInline(
    text: String,
    p: MdPalette,
    onWiki: (String) -> Unit,
    onTag: (String) -> Unit,
): AnnotatedString {
    val b = AnnotatedString.Builder()
    appendInline(b, text, p, onWiki, onTag)
    return b.toAnnotatedString()
}

private data class Tok(val start: Int, val end: Int, val kind: Int, val m: MatchResult)

private fun appendInline(b: AnnotatedString.Builder, text: String, p: MdPalette, onWiki: (String) -> Unit, onTag: (String) -> Unit) {
    val linkStyle = TextLinkStyles(SpanStyle(color = p.accent, textDecoration = TextDecoration.Underline))
    val wikiStyle = TextLinkStyles(SpanStyle(color = p.accent, fontWeight = FontWeight.Medium))
    val tagStyle = TextLinkStyles(SpanStyle(color = p.accent, fontWeight = FontWeight.Medium))
    val patterns = listOf(codeSpanRx, wikiRx, linkRx, boldRx, strikeRx, highlightRx, italicStarRx, italicUnderRx, urlRx)
    var pos = 0
    while (pos < text.length) {
        var best: Tok? = null
        patterns.forEachIndexed { k, rx ->
            val m = rx.find(text, pos)
            if (m != null && (best == null || m.range.first < best!!.start)) best = Tok(m.range.first, m.range.last + 1, k, m)
        }
        val tagRange = TextTools.hashtagRanges(text).firstOrNull { it.first >= pos }
        val tagFirst = tagRange != null && (best == null || tagRange.first < best!!.start)
        if (tagFirst) {
            appendPlain(b, text.substring(pos, tagRange!!.first))
            val tag = text.substring(tagRange.first + 1, tagRange.last + 1)
            b.withLink(LinkAnnotation.Clickable("tag:$tag", tagStyle) { onTag(tag) }) { append(text.substring(tagRange.first, tagRange.last + 1)) }
            pos = tagRange.last + 1
            continue
        }
        val t = best ?: run { appendPlain(b, text.substring(pos)); return }
        appendPlain(b, text.substring(pos, t.start))
        val g = t.m.groupValues
        when (t.kind) {
            0 -> { b.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = p.codeBg, fontSize = p.baseSize * 0.92f)); b.append(t.m.value.trim('`')); b.pop() }
            1 -> {
                val target = g[1].trim()
                b.withLink(LinkAnnotation.Clickable("wiki:$target", wikiStyle) { onWiki(target) }) { append(g[2].ifEmpty { g[1] }) }
            }
            2 -> b.withLink(LinkAnnotation.Url(normalizeUrl(g[2]), linkStyle)) { appendInline(this, g[1], p, onWiki, onTag) }
            3 -> { b.pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); appendInline(b, g[2], p, onWiki, onTag); b.pop() }
            4 -> { b.pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = p.muted)); appendInline(b, g[1], p, onWiki, onTag); b.pop() }
            5 -> { b.pushStyle(SpanStyle(background = p.highlight)); appendInline(b, g[1], p, onWiki, onTag); b.pop() }
            6, 7 -> { b.pushStyle(SpanStyle(fontStyle = FontStyle.Italic)); appendInline(b, g[1], p, onWiki, onTag); b.pop() }
            8 -> b.withLink(LinkAnnotation.Url(g[1], linkStyle)) { append(g[1]) }
        }
        pos = t.end
    }
}

private fun appendPlain(b: AnnotatedString.Builder, s: String) { if (s.isNotEmpty()) b.append(s) }

private fun normalizeUrl(u: String) = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(u)) u else "https://$u"
