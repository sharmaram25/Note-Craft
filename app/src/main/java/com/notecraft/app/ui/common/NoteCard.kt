package com.notecraft.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notecraft.app.data.db.NoteWithMeta
import com.notecraft.app.domain.TextTools
import com.notecraft.app.ui.theme.NC
import com.notecraft.app.ui.theme.NoteColors

private val onlyTags = Regex("""^\s*(#[\p{L}\p{N}_/\-]+\s*)+$""")

@Composable
fun NoteCard(
    item: NoteWithMeta,
    compact: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTrashInfo: String? = null,
) {
    val note = item.note
    val bg = NC.noteBg(note.color)
    val extra = NC.extra
    val shape = RoundedCornerShape(18.dp)
    val borderColor by animateColorAsState(
        when {
            selected -> MaterialTheme.colorScheme.primary
            note.color == 0 -> extra.cardBorder
            else -> NoteColors[note.color].swatch.copy(alpha = if (extra.isDark) 0.18f else 0.28f)
        }, label = "border"
    )
    val scale by animateFloatAsState(if (selected) 0.97f else 1f, label = "scale")

    val preview = remember(note.body, note.isList) {
        if (note.isList) "" else TextTools.plainPreview(note.body.lineSequence().filterNot { onlyTags.matches(it) }.joinToString("\n"), if (compact) 220 else 500)
    }
    val tags = remember(note.body) { if (note.isList) emptyList() else TextTools.hashtags(note.body).take(3) }
    val tasks = remember(note.body, note.isList) { if (note.isList) TextTools.parseTasks(note.body) else emptyList() }

    Box(
        modifier
            .scale(scale)
            .clip(shape)
            .background(bg)
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column {
            if (item.coverFile != null && !note.locked && !compact) {
                AttachmentImage(item.coverFile, Modifier.fillMaxWidth().aspectRatio(1.6f), reqSizePx = 480)
            }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    if (note.title.isNotBlank()) {
                        Text(
                            note.title, style = MaterialTheme.typography.titleMedium, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                        )
                    } else Spacer(Modifier.weight(1f))
                    if (note.pinned) Icon(Icons.Rounded.PushPin, null, tint = extra.muted, modifier = Modifier.padding(start = 6.dp, top = 2.dp).size(14.dp))
                    if (compact && item.coverFile != null && !note.locked) {
                        AttachmentImage(item.coverFile, Modifier.padding(start = 10.dp).size(44.dp).clip(RoundedCornerShape(10.dp)), reqSizePx = 160)
                    }
                }

                when {
                    note.locked -> {
                        if (note.title.isNotBlank()) Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Lock, null, tint = extra.muted, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Locked", style = MaterialTheme.typography.bodyMedium, color = extra.muted)
                        }
                    }
                    note.isList -> ChecklistPreview(tasks, item.itemCount, item.checkedCount, compact, hasTitle = note.title.isNotBlank())
                    preview.isNotEmpty() -> {
                        if (note.title.isNotBlank()) Spacer(Modifier.height(5.dp))
                        Text(
                            preview,
                            style = if (note.title.isBlank()) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (note.title.isBlank()) 0.92f else 0.72f),
                            maxLines = if (compact) 3 else 9, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    note.title.isBlank() && item.attachmentCount == 0 -> Text("Empty note", style = MaterialTheme.typography.bodyMedium, color = extra.faint)
                }

                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item.nextReminder?.let {
                        val overdue = it < System.currentTimeMillis()
                        Pill(Fmt.due(it), Icons.Rounded.Alarm, tint = if (overdue) extra.danger else MaterialTheme.colorScheme.primary)
                    }
                    if (!note.locked) tags.forEach { Pill("#$it") }
                    if (item.attachmentCount > 1) Pill("${item.attachmentCount}", Icons.Outlined.Image)
                    if (showTrashInfo != null) Pill(showTrashInfo, tint = extra.danger)
                    Text(
                        Fmt.relative(note.updatedAt), style = MaterialTheme.typography.labelSmall, color = extra.faint,
                        modifier = Modifier.align(Alignment.CenterVertically).padding(vertical = 2.dp)
                    )
                }
            }
        }
        if (selectionMode) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp).clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .border(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else extra.faint, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun ChecklistPreview(tasks: List<TextTools.TaskLine>, total: Int, done: Int, compact: Boolean, hasTitle: Boolean) {
    val extra = NC.extra
    if (hasTitle) Spacer(Modifier.height(8.dp))
    if (total > 0) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else done / total.toFloat() },
                modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape),
                color = if (done == total) extra.success else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
            )
            Spacer(Modifier.width(8.dp))
            Text("$done/$total", style = MaterialTheme.typography.labelSmall, color = extra.muted)
        }
    }
    val shown = tasks.filter { !it.checked }.take(if (compact) 2 else 6)
    if (shown.isNotEmpty()) Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.heightIn(max = 200.dp)) {
        shown.forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (t.checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                    tint = if (t.checked) extra.success else extra.faint, modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    TextTools.inlineStrip(t.text), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (t.checked) TextDecoration.LineThrough else null,
                )
            }
        }
        val remaining = tasks.count { !it.checked } - shown.size
        if (remaining > 0) Text("+$remaining more", style = MaterialTheme.typography.labelSmall, color = extra.muted, modifier = Modifier.padding(start = 22.dp))
        if (total > 0 && done == total) Text("All done", style = MaterialTheme.typography.labelMedium, color = extra.success)
        if (total == 0) Text("Empty list", style = MaterialTheme.typography.bodyMedium, color = extra.faint)
    }
}
