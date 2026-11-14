package com.notecraft.app.ui.reminders

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.notecraft.app.AppContainer
import com.notecraft.app.data.db.NoteKind
import com.notecraft.app.data.db.ReminderEntity
import com.notecraft.app.data.db.ReminderWithNote
import com.notecraft.app.data.db.Repeat
import com.notecraft.app.domain.NaturalDate
import com.notecraft.app.ui.LocalNavigator
import com.notecraft.app.ui.common.EmptyState
import com.notecraft.app.ui.common.Fmt
import com.notecraft.app.ui.common.LocalSnackbar
import com.notecraft.app.ui.common.SectionLabel
import com.notecraft.app.ui.common.appViewModel
import com.notecraft.app.ui.common.rememberHaptics
import com.notecraft.app.ui.common.showUndo
import com.notecraft.app.ui.theme.NC
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Immutable
data class ReminderGroup(val label: String, val items: List<ReminderWithNote>, val overdue: Boolean = false)

class RemindersViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repository
    val groups = repo.observeReminders().map { all -> group(all) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun group(all: List<ReminderWithNote>): Pair<List<ReminderGroup>, List<ReminderWithNote>> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = System.currentTimeMillis()
        val pending = all.filter { !it.reminder.done }
        val done = all.filter { it.reminder.done }.sortedByDescending { it.reminder.doneAt ?: it.reminder.dueAt }
        fun date(r: ReminderWithNote) = Instant.ofEpochMilli(r.reminder.dueAt).atZone(zone).toLocalDate()
        val overdue = pending.filter { it.reminder.dueAt < now }
        val upcoming = pending.filter { it.reminder.dueAt >= now }
        val groups = listOf(
            ReminderGroup("Overdue", overdue, overdue = true),
            ReminderGroup("Today", upcoming.filter { date(it) == today }),
            ReminderGroup("Tomorrow", upcoming.filter { date(it) == today.plusDays(1) }),
            ReminderGroup("This week", upcoming.filter { date(it).isAfter(today.plusDays(1)) && date(it).isBefore(today.plusDays(7)) }),
            ReminderGroup("Later", upcoming.filter { !date(it).isBefore(today.plusDays(7)) }),
        ).filter { it.items.isNotEmpty() }
        return groups to done
    }

    fun add(title: String, due: Long, repeat: Int) = viewModelScope.launch { repo.addReminder(title, due, repeat, null) }
    fun update(r: ReminderEntity, title: String, due: Long, repeat: Int) = viewModelScope.launch {
        repo.updateReminder(r.copy(title = title, dueAt = due, repeat = repeat, done = false, doneAt = null))
    }
    fun complete(id: Long) = viewModelScope.launch { repo.completeReminder(id) }
    fun reopen(id: Long) = viewModelScope.launch { repo.reopenReminder(id) }
    fun delete(r: ReminderEntity) = viewModelScope.launch { repo.deleteReminder(r) }
    fun restore(r: ReminderEntity) = viewModelScope.launch { repo.restoreReminder(r) }
    fun clearCompleted() = viewModelScope.launch { repo.clearCompletedReminders() }
}

@Composable
fun RemindersScreen(addSignal: Int) {
    val vm = appViewModel(key = "reminders") { RemindersViewModel(it) }
    val data by vm.groups.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<ReminderEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    var permTick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { permTick++ } }
    var lastSignal by rememberSaveable { mutableIntStateOf(addSignal) }
    LaunchedEffect(addSignal) { if (addSignal != lastSignal) { lastSignal = addSignal; creating = true } }

    val notifGranted = remember(permTick) {
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }

    fun deleteWithUndo(r: ReminderEntity) {
        haptics.confirm(); vm.delete(r)
        scope.launch { snackbar.showUndo("Reminder deleted") { vm.restore(r) } }
    }

    val (groups, done) = data ?: (emptyList<ReminderGroup>() to emptyList())
    LazyColumn(contentPadding = PaddingValues(bottom = 140.dp), modifier = Modifier.fillMaxSize()) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item {
            Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.today(), style = MaterialTheme.typography.labelLarge, color = NC.extra.muted, modifier = Modifier.weight(1f))
                    IconButton(onClick = navigator::settings) { Icon(Icons.Outlined.Settings, "Settings", tint = NC.extra.muted) }
                }
                Text("Reminders", style = MaterialTheme.typography.displaySmall)
            }
        }
        item { QuickAdd(onAdd = { t, due, rep -> haptics.confirm(); vm.add(t, due, rep); if (!notifGranted && Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) }
        if (!notifGranted) item {
            Banner(
                "Notifications are off", "Turn them on so reminders can reach you.",
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            )
        }
        if (data != null && groups.isEmpty()) item {
            EmptyState(
                Icons.Rounded.AlarmOn, if (done.isEmpty()) "Nothing to remember" else "All caught up",
                "Type something like “Water the plants every Sunday 9am” above."
            )
        }
        groups.forEach { g ->
            item(key = "h-${g.label}") {
                SectionLabel(g.label, Modifier.padding(start = 24.dp, top = 18.dp, bottom = 6.dp).animateItem())
            }
            items(g.items, key = { it.reminder.id }) { r ->
                SwipeRow(Modifier.animateItem(), onDelete = { deleteWithUndo(r.reminder) }) {
                    ReminderRow(
                        r, overdue = g.overdue,
                        onCheck = { haptics.confirm(); vm.complete(r.reminder.id) },
                        onClick = { editing = r.reminder },
                        onOpenNote = { r.reminder.noteId?.let { navigator.open(it, r.noteKind == NoteKind.LIST) } },
                    )
                }
            }
        }
        if (done.isNotEmpty()) {
            item(key = "done-header") {
                val rot by androidx.compose.animation.core.animateFloatAsState(if (showDone) 0f else -90f, label = "rot")
                Row(
                    Modifier.fillMaxWidth().animateItem().clickable { showDone = !showDone }.padding(start = 20.dp, end = 8.dp, top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.ExpandMore, null, tint = NC.extra.muted, modifier = Modifier.size(20.dp).rotate(rot))
                    Spacer(Modifier.width(6.dp))
                    SectionLabel("Completed · ${done.size}", Modifier.weight(1f))
                    if (showDone) TextButton(onClick = { haptics.confirm(); vm.clearCompleted() }) { Text("Clear", color = NC.extra.muted) }
                }
            }
            if (showDone) items(done, key = { "d-${it.reminder.id}" }) { r ->
                SwipeRow(Modifier.animateItem(), onDelete = { deleteWithUndo(r.reminder) }) {
                    ReminderRow(r, overdue = false, onCheck = { haptics.tick(); vm.reopen(r.reminder.id) }, onClick = { editing = r.reminder }, onOpenNote = {
                        r.reminder.noteId?.let { navigator.open(it, r.noteKind == NoteKind.LIST) }
                    })
                }
            }
        }
    }

    if (creating) ReminderSheet(initial = null, onSave = { t, due, rep -> vm.add(t, due, rep) }, onDismiss = { creating = false })
    editing?.let { r ->
        ReminderSheet(
            initial = r, onSave = { t, due, rep -> vm.update(r, t, due, rep) },
            onDelete = { deleteWithUndo(r) }, onDismiss = { editing = null },
        )
    }
}

@Composable
private fun QuickAdd(onAdd: (String, Long, Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val parsed = remember(text) { NaturalDate.parse(text) }
    val canAdd = parsed.title.isNotBlank() || text.isNotBlank()
    fun submit() {
        if (!canAdd) return
        val title = parsed.title.ifBlank { text.trim() }
        onAdd(title, parsed.dueAt ?: NaturalDate.defaultDue(), parsed.repeat)
        text = ""
        focus.clearFocus()
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, NC.extra.cardBorder, RoundedCornerShape(20.dp)).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Add, null, tint = NC.extra.muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = text, onValueChange = { text = it.replace("\n", "") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                decorationBox = { inner -> Box { if (text.isEmpty()) Text("Remind me to…", color = NC.extra.muted, style = MaterialTheme.typography.bodyLarge); inner() } }
            )
            AnimatedVisibility(canAdd) {
                IconButton(onClick = ::submit, modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) {
                    Icon(Icons.Rounded.ArrowUpward, "Add", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        AnimatedVisibility(text.isNotBlank()) {
            Row(Modifier.padding(start = 18.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    buildString {
                        append(Fmt.due(parsed.dueAt ?: NaturalDate.defaultDue()))
                        if (parsed.repeat != Repeat.NONE) append(" · ${repeatLabel(parsed.repeat).lowercase()}")
                        if (parsed.dueAt == null) append("  (add a time like “at 6pm”)")
                    },
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun Banner(title: String, text: String, onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .background(NC.extra.danger.copy(alpha = 0.08f)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.NotificationsOff, null, tint = NC.extra.danger)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall, color = NC.extra.muted)
        }
        Text("Allow", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SwipeRow(modifier: Modifier, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { it * 0.35f })
    LaunchedEffect(state.currentValue) { if (state.currentValue == SwipeToDismissBoxValue.EndToStart) onDelete() }
    SwipeToDismissBox(
        state, modifier = modifier, enableDismissFromStartToEnd = false,
        backgroundContent = {
            val c by animateColorAsState(if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) NC.extra.danger else Color.Transparent, label = "bg")
            Box(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 3.dp).clip(MaterialTheme.shapes.medium).background(c.copy(alpha = 0.15f)).padding(end = 20.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Rounded.Delete, null, tint = c)
            }
        }
    ) { content() }
}

@Composable
private fun ReminderRow(r: ReminderWithNote, overdue: Boolean, onCheck: () -> Unit, onClick: () -> Unit, onOpenNote: () -> Unit) {
    val rem = r.reminder
    val extra = NC.extra
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 3.dp).fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest).border(1.dp, extra.cardBorder, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape)
                .background(if (rem.done) MaterialTheme.colorScheme.primary else Color.Transparent)
                .border(1.8.dp, if (rem.done) MaterialTheme.colorScheme.primary else if (overdue) extra.danger else extra.faint, CircleShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCheck),
            contentAlignment = Alignment.Center
        ) { if (rem.done) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                rem.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (rem.done) extra.muted else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (rem.done) TextDecoration.LineThrough else null,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(Fmt.due(rem.dueAt), style = MaterialTheme.typography.labelMedium, color = if (overdue) extra.danger else extra.muted)
                if (rem.repeat != Repeat.NONE) {
                    Icon(Icons.Rounded.Repeat, null, tint = extra.muted, modifier = Modifier.size(13.dp))
                    Text(repeatLabel(rem.repeat), style = MaterialTheme.typography.labelMedium, color = extra.muted)
                }
            }
        }
        if (rem.noteId != null) {
            Row(
                Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)).clickable(onClick = onOpenNote)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(if (r.noteKind == NoteKind.LIST) Icons.Outlined.Checklist else Icons.AutoMirrored.Outlined.Notes, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text((r.noteTitle ?: "").ifBlank { "Note" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 110.dp))
            }
        }
    }
}

