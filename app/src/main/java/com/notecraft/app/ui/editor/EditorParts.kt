package com.notecraft.app.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.rounded.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.notecraft.app.data.db.AttachmentEntity
import com.notecraft.app.ui.common.AttachmentImage
import com.notecraft.app.ui.common.rememberHaptics
import com.notecraft.app.ui.theme.NC

// ---- Reading mode ----------------------------------------------------------------------------

@Composable
fun ReadView(
    body: String,
    palette: MdPalette,
    textStyle: TextStyle,
    onToggleTask: (Int) -> Unit,
    onWiki: (String) -> Unit,
    onTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(body) { parseBlocks(body) }
    val haptics = rememberHaptics()
    val base = palette.baseSize
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (blocks.isEmpty()) Text("Nothing here yet. Tap the pencil to start writing.", style = textStyle.copy(color = NC.extra.faint))
        blocks.forEach { b ->
            when (b) {
                is MdBlock.Heading -> {
                    val scale = when (b.level) { 1 -> 1.55f; 2 -> 1.32f; 3 -> 1.16f; else -> 1.05f }
                    Text(
                        renderInline(b.text, palette, onWiki, onTag),
                        style = textStyle.copy(fontSize = base * scale, fontWeight = FontWeight.Bold, lineHeight = base * scale * 1.3f, letterSpacing = (-0.2).sp),
                        modifier = Modifier.padding(top = if (b.level <= 2) 14.dp else 8.dp, bottom = 2.dp)
                    )
                }
                is MdBlock.Paragraph -> Text(renderInline(b.text, palette, onWiki, onTag), style = textStyle)
                is MdBlock.Bullet -> ListRow(b.indent, { Text("•", style = textStyle.copy(color = palette.accent, fontWeight = FontWeight.Bold)) }) {
                    Text(renderInline(b.text, palette, onWiki, onTag), style = textStyle)
                }
                is MdBlock.Ordered -> ListRow(b.indent, { Text(b.marker, style = textStyle.copy(color = palette.accent, fontWeight = FontWeight.SemiBold)) }) {
                    Text(renderInline(b.text, palette, onWiki, onTag), style = textStyle)
                }
                is MdBlock.Task -> Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { haptics.tick(); onToggleTask(b.line) }
                        .padding(start = (b.indent * 20).dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        if (b.checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null,
                        tint = if (b.checked) palette.accent else NC.extra.faint,
                        modifier = Modifier.padding(top = 2.dp).size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        renderInline(b.text, palette, onWiki, onTag),
                        style = textStyle.copy(
                            color = if (b.checked) palette.muted else textStyle.color,
                            textDecoration = if (b.checked) TextDecoration.LineThrough else null
                        )
                    )
                }
                is MdBlock.Quote -> Row(Modifier.padding(vertical = 4.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().clip(CircleShape).background(palette.accent.copy(alpha = 0.6f)))
                    Spacer(Modifier.width(12.dp))
                    Text(renderInline(b.text, palette, onWiki, onTag), style = textStyle.copy(color = palette.muted, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                }
                is MdBlock.Code -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(palette.codeBg)
                        .horizontalScroll(rememberScrollState()).padding(14.dp)
                ) { Text(b.text, style = textStyle.copy(fontFamily = FontFamily.Monospace, fontSize = base * 0.88f, lineHeight = base * 1.3f)) }
                MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 14.dp), color = palette.syntax.copy(alpha = 0.5f))
                MdBlock.Gap -> Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ListRow(indent: Int, marker: @Composable () -> Unit, content: @Composable () -> Unit) {
    Row(Modifier.padding(start = (indent * 20).dp, top = 1.dp, bottom = 1.dp)) {
        Box(Modifier.width(22.dp), contentAlignment = Alignment.TopCenter) { marker() }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

// ---- Formatting toolbar ------------------------------------------------------------------------

@Composable
fun FormatToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onOp: ((androidx.compose.ui.text.input.TextFieldValue) -> androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    onImage: () -> Unit,
    onHideKeyboard: () -> Unit,
    titleMode: Boolean,
) {
    val haptics = rememberHaptics()
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 8.dp) {
        Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Tool(Icons.AutoMirrored.Rounded.Undo, "Undo", canUndo) { haptics.tick(); onUndo() }
                Tool(Icons.AutoMirrored.Rounded.Redo, "Redo", canRedo) { haptics.tick(); onRedo() }
                if (!titleMode) {
                    Divider()
                    Tool(Icons.Rounded.CheckBox, "Checklist") { onOp { EditorOps.toggleLinePrefix(it, LinePrefix.TASK) } }
                    Tool(Icons.Rounded.Title, "Heading") { onOp { EditorOps.toggleLinePrefix(it, LinePrefix.HEADING) } }
                    Tool(Icons.Rounded.FormatBold, "Bold") { onOp { EditorOps.toggleWrap(it, "**") } }
                    Tool(Icons.Rounded.FormatItalic, "Italic") { onOp { EditorOps.toggleWrap(it, "*") } }
                    Tool(Icons.Rounded.BorderColor, "Highlight") { onOp { EditorOps.toggleWrap(it, "==") } }
                    Tool(Icons.Rounded.FormatStrikethrough, "Strikethrough") { onOp { EditorOps.toggleWrap(it, "~~") } }
                    Divider()
                    Tool(Icons.AutoMirrored.Rounded.FormatListBulleted, "Bullets") { onOp { EditorOps.toggleLinePrefix(it, LinePrefix.BULLET) } }
                    Tool(Icons.Rounded.FormatListNumbered, "Numbered list") { onOp { EditorOps.toggleLinePrefix(it, LinePrefix.NUMBER) } }
                    Tool(Icons.Rounded.FormatQuote, "Quote") { onOp { EditorOps.toggleLinePrefix(it, LinePrefix.QUOTE) } }
                    Tool(Icons.AutoMirrored.Rounded.FormatIndentIncrease, "Indent") { onOp { EditorOps.indent(it, outdent = false) } }
                    Tool(Icons.AutoMirrored.Rounded.FormatIndentDecrease, "Outdent") { onOp { EditorOps.indent(it, outdent = true) } }
                    Divider()
                    Tool(Icons.Rounded.Link, "Link") { onOp { EditorOps.insertLink(it) } }
                    Tool(Icons.Rounded.NoteAdd, "Link a note") { onOp { EditorOps.insertAtCursor(it, "[[") } }
                    Tool(Icons.Outlined.AddPhotoAlternate, "Add image") { onImage() }
                    Tool(Icons.Rounded.Code, "Inline code") { onOp { EditorOps.toggleWrap(it, "`") } }
                    Tool(Icons.Rounded.DataObject, "Code block") { onOp { EditorOps.insertBlock(it, "```\n\n```\n", 4) } }
                    Tool(Icons.Rounded.HorizontalRule, "Divider") { onOp { EditorOps.insertBlock(it, "---\n") } }
                }
            }
            VerticalDivider(Modifier.height(26.dp), color = NC.extra.cardBorder)
            IconButton(onClick = onHideKeyboard) { Icon(Icons.Rounded.KeyboardHide, "Hide keyboard", tint = NC.extra.muted) }
        }
    }
}

@Composable
private fun Tool(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp)) {
        Icon(icon, label, tint = if (enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f) else NC.extra.faint, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun Divider() {
    Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(22.dp).background(NC.extra.cardBorder))
}

/** Suggestions shown while typing `[[partial` so linking notes is effortless. */
@Composable
fun WikiSuggestions(items: List<String>, query: String, onPick: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { t ->
                Text(
                    "[[ $t ]]", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .clickable { onPick(t) }.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            if (query.isNotBlank() && items.none { it.equals(query, true) }) item {
                Text(
                    "+ New “$query”", style = MaterialTheme.typography.labelLarge, color = NC.extra.muted,
                    modifier = Modifier.clip(CircleShape).background(NC.extra.cardBorder.copy(alpha = 0.5f))
                        .clickable { onPick(query) }.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

// ---- Images ------------------------------------------------------------------------------------

@Composable
fun AttachmentStrip(items: List<AttachmentEntity>, onOpen: (AttachmentEntity) -> Unit) {
    if (items.isEmpty()) return
    if (items.size == 1) {
        val a = items.first()
        val ratio = (a.width.toFloat() / a.height.coerceAtLeast(1)).coerceIn(0.75f, 2.2f)
        AttachmentImage(
            a.fileName, reqSizePx = 1080,
            modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .then(Modifier.height((340 / ratio).coerceIn(160f, 360f).dp)).clickable { onOpen(a) }
        )
        return
    }
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = { it.id }) { a ->
            val ratio = (a.width.toFloat() / a.height.coerceAtLeast(1)).coerceIn(0.6f, 1.8f)
            AttachmentImage(
                a.fileName, reqSizePx = 600,
                modifier = Modifier.height(180.dp).width((180 * ratio).dp).clip(RoundedCornerShape(14.dp)).clickable { onOpen(a) }
            )
        }
    }
}

@Composable
fun ImageViewer(a: AttachmentEntity, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AttachmentImage(
                a.fileName, reqSizePx = 2048, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f }) }
                    .transformable(state)
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
            )
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close", tint = Color.White) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onDelete(); onDismiss() }) { Icon(Icons.Outlined.DeleteOutline, "Delete image", tint = Color.White) }
            }
        }
    }
}

// ---- Locked note -------------------------------------------------------------------------------

@Composable
fun LockedPlaceholder(onUnlock: () -> Unit) {
    Column(Modifier.fillMaxSize().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("This note is locked", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text("Verify it's you to read it.", color = NC.extra.muted)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onUnlock, shape = CircleShape) { Text("Unlock") }
        Spacer(Modifier.height(80.dp))
    }
}
