package com.notecraft.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.AddAlert
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notecraft.app.data.db.NoteEntity
import com.notecraft.app.data.db.RevisionEntity
import com.notecraft.app.domain.TextTools
import com.notecraft.app.ui.common.ColorPickerRow
import com.notecraft.app.ui.common.EmptyState
import com.notecraft.app.ui.common.Fmt
import com.notecraft.app.ui.theme.NC

data class SheetAction(val icon: ImageVector, val label: String, val danger: Boolean = false, val onClick: () -> Unit)

@Composable
fun OptionsSheet(
    color: Int,
    onColor: (Int) -> Unit,
    actions: List<SheetAction>,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp).verticalScroll(rememberScrollState())) {
            ColorPickerRow(color, onColor)
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = NC.extra.cardBorder)
            Spacer(Modifier.height(6.dp))
            actions.forEach { a -> ActionRow(a) { onDismiss(); a.onClick() } }
        }
    }
}

@Composable
private fun ActionRow(a: SheetAction, onClick: () -> Unit) {
    val tint = if (a.danger) NC.extra.danger else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(a.icon, null, tint = if (a.danger) tint else NC.extra.muted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(a.label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

object NoteActions {
    fun build(
        isNew: Boolean,
        note: NoteEntity?,
        isList: Boolean,
        hasReminder: Boolean,
        onImage: (() -> Unit)?,
        onReminder: () -> Unit,
        onConvert: () -> Unit,
        onLock: () -> Unit,
        onShare: () -> Unit,
        onExport: (() -> Unit)?,
        onHistory: (() -> Unit)?,
        onInfo: (() -> Unit)?,
        onCopy: () -> Unit,
        onArchive: () -> Unit,
        onDelete: () -> Unit,
    ): List<SheetAction> = buildList {
        if (onImage != null) add(SheetAction(Icons.Outlined.AddPhotoAlternate, "Add image", onClick = onImage))
        add(SheetAction(Icons.Outlined.AddAlert, if (hasReminder) "Edit reminder" else "Remind me", onClick = onReminder))
        add(SheetAction(if (isList) Icons.AutoMirrored.Outlined.Notes else Icons.Outlined.Checklist, if (isList) "Convert to note" else "Convert to checklist", onClick = onConvert))
        add(SheetAction(if (note?.locked == true) Icons.Outlined.LockOpen else Icons.Outlined.Lock, if (note?.locked == true) "Remove lock" else "Lock note", onClick = onLock))
        add(SheetAction(Icons.Outlined.Share, "Share", onClick = onShare))
        if (onExport != null) add(SheetAction(Icons.AutoMirrored.Outlined.InsertDriveFile, "Export as Markdown", onClick = onExport))
        if (onHistory != null && !isNew) add(SheetAction(Icons.Outlined.History, "Version history", onClick = onHistory))
        if (onInfo != null) add(SheetAction(Icons.Outlined.Info, "Note info", onClick = onInfo))
        if (!isNew) add(SheetAction(Icons.Outlined.ContentCopy, "Make a copy", onClick = onCopy))
        if (!isNew) add(SheetAction(if (note?.archived == true) Icons.Outlined.Unarchive else Icons.Outlined.Archive, if (note?.archived == true) "Unarchive" else "Archive", onClick = onArchive))
        add(SheetAction(Icons.Outlined.DeleteOutline, "Delete", danger = true, onClick = onDelete))
    }
}

@Composable
fun InfoSheet(
    note: NoteEntity?,
    title: String,
    body: String,
    loadBacklinks: suspend () -> List<NoteEntity>,
    onOpen: (NoteEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val words = remember(body, title) { TextTools.wordCount("$title $body") }
    var backlinks by remember { mutableStateOf<List<NoteEntity>?>(null) }
    LaunchedEffect(note?.id) { backlinks = loadBacklinks() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Note info", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("$words", "words", Modifier.weight(1f))
                Stat("${body.length}", "characters", Modifier.weight(1f))
                Stat("${TextTools.readingMinutes(words)} min", "read", Modifier.weight(1f))
            }
            Spacer(Modifier.height(18.dp))
            if (note != null) {
                InfoLine("Created", Fmt.fullWithTime(note.createdAt))
                InfoLine("Last edited", Fmt.fullWithTime(note.updatedAt))
            }
            val tags = remember(body) { TextTools.hashtags(body) }
            if (tags.isNotEmpty()) InfoLine("Tags", tags.joinToString("  ") { "#$it" })
            Spacer(Modifier.height(16.dp))
            Text("Linked from", style = MaterialTheme.typography.labelLarge, color = NC.extra.muted)
            Spacer(Modifier.height(8.dp))
            when {
                backlinks == null -> Text("…", color = NC.extra.faint)
                backlinks!!.isEmpty() -> Text(
                    if (title.isBlank()) "Give this note a title, then link to it from others with [[$title]]." else "No notes link here yet. Type [[${title.trim()}]] in another note.",
                    style = MaterialTheme.typography.bodyMedium, color = NC.extra.faint
                )
                else -> backlinks!!.forEach { n ->
                    Text(
                        n.title.ifBlank { "Untitled" }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onDismiss(); onOpen(n) }.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelMedium, color = NC.extra.muted)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = NC.extra.muted, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun RevisionsSheet(revisions: List<RevisionEntity>, onRestore: (RevisionEntity) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var preview by remember { mutableStateOf<RevisionEntity?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.navigationBarsPadding()) {
            Text("Version history", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                "A snapshot is kept each time you finish editing. Tap one to preview.",
                style = MaterialTheme.typography.bodyMedium, color = NC.extra.muted, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            if (revisions.isEmpty()) EmptyState(Icons.Rounded.History, "No versions yet", "Earlier versions appear here after you edit this note.")
            else LazyColumn(Modifier.heightIn(max = 520.dp).padding(top = 8.dp, bottom = 16.dp)) {
                items(revisions, key = { it.id }) { r ->
                    Row(
                        Modifier.fillMaxWidth().clickable { preview = r }.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(Fmt.fullWithTime(r.savedAt), style = MaterialTheme.typography.titleSmall)
                            Text(
                                (r.title.takeIf { it.isNotBlank() }?.plus(" — ") ?: "") + TextTools.plainPreview(r.body, 120).replace('\n', ' '),
                                style = MaterialTheme.typography.bodySmall, color = NC.extra.muted, maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
    preview?.let { r ->
        AlertDialog(
            onDismissRequest = { preview = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(r.title.ifBlank { "Untitled" }) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(Fmt.fullWithTime(r.savedAt), style = MaterialTheme.typography.labelMedium, color = NC.extra.muted)
                    Spacer(Modifier.height(10.dp))
                    Text(r.body, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { onRestore(r); preview = null; onDismiss() }) { Text("Restore this version") } },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("Close", color = Color.Unspecified) } },
        )
    }
}
