package com.notecraft.app.ui.lists

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notecraft.app.data.db.ChecklistItemEntity
import com.notecraft.app.ui.LocalAuth
import com.notecraft.app.ui.LocalNavigator
import com.notecraft.app.ui.common.LocalContainer
import com.notecraft.app.ui.common.LocalSnackbar
import com.notecraft.app.ui.common.appViewModel
import com.notecraft.app.ui.common.rememberHaptics
import com.notecraft.app.ui.common.sharedNote
import com.notecraft.app.ui.common.showUndo
import com.notecraft.app.ui.editor.LockedPlaceholder
import com.notecraft.app.ui.editor.NoteActions
import com.notecraft.app.ui.editor.OptionsSheet
import com.notecraft.app.ui.editor.SheetAction
import com.notecraft.app.ui.reminders.ReminderSheet
import com.notecraft.app.ui.theme.NC
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ListEditorScreen(id: Long, origin: String?) {
    val vm = appViewModel(key = "list-$id") { ListEditorViewModel(it, id) }
    val container = LocalContainer.current
    val navigator = LocalNavigator.current
    val snackbar = LocalSnackbar.current
    val auth = LocalAuth.current
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val note by vm.note.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsState(initial = null)
    val moveChecked = settings?.moveCheckedToBottom ?: true
    var showCompleted by rememberSaveable { mutableStateOf(true) }
    var options by remember { mutableStateOf(false) }
    var reminderSheet by remember { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf(id <= 0 || vm.isUnlocked()) }
    val extra = NC.extra
    val listState = rememberLazyListState()
    val titleFocus = remember { FocusRequester() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) vm.syncInBackground() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(vm.focusTitle, vm.loaded) { if (vm.focusTitle && vm.loaded) { titleFocus.requestFocus(); vm.focusTitle = false } }

    val locked = note?.locked == true && !unlocked
    fun promptUnlock() = auth.authenticate("Unlock list", note?.title) { ok -> if (ok) { vm.markUnlocked(); unlocked = true } }
    LaunchedEffect(note?.locked) { if (note?.locked == true && !unlocked) promptUnlock() }

    val bg by animateColorAsState(if ((note?.color ?: 0) == 0) MaterialTheme.colorScheme.background else NC.noteBg(note!!.color), tween(300), label = "bg")
    val unchecked = if (moveChecked) vm.items.filter { !it.checked } else vm.items.toList()
    val checked = if (moveChecked) vm.items.filter { it.checked } else emptyList()
    val done = vm.doneCount; val total = vm.totalCount
    val progress by animateFloatAsState(if (total == 0) 0f else done / total.toFloat(), label = "progress")

    // Drag & drop state
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Box(Modifier.fillMaxSize().sharedNote(origin).background(bg)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = navigator::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Spacer(Modifier.weight(1f))
                if (!locked) {
                    IconButton(onClick = { haptics.confirm(); vm.setPinned(!(note?.pinned ?: false)) }) {
                        Icon(if (note?.pinned == true) Icons.Rounded.PushPin else Icons.Outlined.PushPin, "Pin",
                            tint = if (note?.pinned == true) MaterialTheme.colorScheme.primary else extra.muted)
                    }
                    IconButton(onClick = { reminderSheet = true }) {
                        Icon(if (reminders.isNotEmpty()) Icons.Rounded.Alarm else Icons.Outlined.Alarm, "Reminder",
                            tint = if (reminders.isNotEmpty()) MaterialTheme.colorScheme.primary else extra.muted)
                    }
                }
                IconButton(onClick = { options = true }) { Icon(Icons.Rounded.MoreVert, "More options", tint = extra.muted) }
            }

            if (locked) { LockedPlaceholder(::promptUnlock); return@Column }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 160.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "title") {
                    Column(Modifier.padding(horizontal = 24.dp)) {
                        BasicTextField(
                            value = vm.title,
                            onValueChange = { vm.onTitle(it.replace("\n", "")) },
                            textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = {
                                val first = vm.items.firstOrNull { !it.checked }
                                if (first != null) vm.focusRequest = first.id else vm.addAtEnd()
                            }),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).focusRequester(titleFocus),
                            decorationBox = { inner ->
                                Box {
                                    if (vm.title.isEmpty()) Text("List name", style = MaterialTheme.typography.headlineSmall.copy(color = extra.faint, fontWeight = FontWeight.SemiBold))
                                    inner()
                                }
                            }
                        )
                        if (total > 0) Row(Modifier.padding(top = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            LinearProgressIndicator(
                                progress = { progress }, modifier = Modifier.weight(1f).height(5.dp).clip(CircleShape),
                                color = if (done == total) extra.success else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(if (done == total) "All done ✨" else "$done of $total", style = MaterialTheme.typography.labelMedium, color = if (done == total) extra.success else extra.muted)
                        }
                    }
                }

                items(unchecked, key = { "u-${it.id}" }) { item ->
                    val dragging = draggingId == item.id
                    ItemRow(
                        item = item, vm = vm, draggable = moveChecked,
                        modifier = if (dragging) Modifier.zIndex(1f).graphicsLayer { translationY = dragOffset }.shadow(8.dp, MaterialTheme.shapes.small)
                            .background(bg) else Modifier.animateItem(),
                        dragHandle = Modifier.pointerInput(item.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggingId = item.id; dragOffset = 0f; haptics.long() },
                                onDragEnd = { draggingId = null; dragOffset = 0f },
                                onDragCancel = { draggingId = null; dragOffset = 0f },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val did = draggingId ?: return@detectDragGesturesAfterLongPress
                                    dragOffset += autoScroll(listState, did)
                                    val layout = listState.layoutInfo
                                    val current = layout.visibleItemsInfo.firstOrNull { it.key == "u-$did" } ?: return@detectDragGesturesAfterLongPress
                                    val middle = current.offset + dragOffset + current.size / 2f
                                    val target = layout.visibleItemsInfo.firstOrNull { info ->
                                        info.key != current.key && (info.key as? String)?.startsWith("u-") == true &&
                                            middle.toInt() in info.offset..(info.offset + info.size)
                                    } ?: return@detectDragGesturesAfterLongPress
                                    val targetId = (target.key as String).removePrefix("u-").toLong()
                                    vm.move(did, targetId)
                                    haptics.tick()
                                    dragOffset += if (target.index > current.index) -target.size.toFloat() else target.size.toFloat()
                                },
                            )
                        },
                        onDelete = { removed ->
                            val index = vm.items.indexOf(removed)
                            vm.delete(removed.id)
                            if (removed.text.isNotBlank()) container.appScope.launch(Dispatchers.Main) { snackbar.showUndo("Item deleted") { vm.restoreItem(removed, index) } }
                        },
                    )
                }

                item(key = "add") {
                    Row(
                        Modifier.fillMaxWidth().clickable { haptics.tick(); vm.addAtEnd() }.padding(horizontal = 24.dp, vertical = 12.dp).animateItem(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(Modifier.width(22.dp))
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Text("Add item", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                if (checked.isNotEmpty()) {
                    item(key = "completed-header") {
                        val rotation by animateFloatAsState(if (showCompleted) 0f else -90f, label = "chev")
                        Row(
                            Modifier.fillMaxWidth().animateItem().clickable { showCompleted = !showCompleted }.padding(start = 24.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.ExpandMore, null, tint = extra.muted, modifier = Modifier.size(20.dp).rotate(rotation))
                            Spacer(Modifier.width(8.dp))
                            Text("Completed · ${checked.size}", style = MaterialTheme.typography.labelLarge, color = extra.muted, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                haptics.confirm()
                                val removed = vm.deleteChecked()
                                container.appScope.launch(Dispatchers.Main) { snackbar.showUndo("${removed.size} cleared") { vm.restoreMany(removed) } }
                            }) { Text("Clear", color = extra.muted) }
                        }
                    }
                    if (showCompleted) items(checked, key = { "c-${it.id}" }) { item ->
                        ItemRow(item, vm, draggable = false, modifier = Modifier.animateItem(), dragHandle = Modifier, onDelete = { vm.delete(it.id) })
                    }
                }
            }
        }
    }

    if (options) OptionsSheet(
        color = note?.color ?: 0, onColor = { haptics.tick(); vm.setColor(it) },
        actions = buildList {
            add(SheetAction(Icons.Outlined.RemoveDone, "Uncheck all") { vm.uncheckAll() })
            add(SheetAction(Icons.Outlined.DoneAll, "Check all") { vm.checkAll() })
            if (checked.isNotEmpty()) add(SheetAction(Icons.Outlined.DeleteSweep, "Delete checked items") {
                val removed = vm.deleteChecked()
                container.appScope.launch(Dispatchers.Main) { snackbar.showUndo("${removed.size} removed") { vm.restoreMany(removed) } }
            })
            addAll(NoteActions.build(
                isNew = false, note = note, isList = true, hasReminder = reminders.isNotEmpty(),
                onImage = null,
                onReminder = { reminderSheet = true },
                onConvert = { vm.convertToNote { nid -> navigator.back(); navigator.open(nid, false) } },
                onLock = {
                    val lockedNow = note?.locked == true
                    if (!auth.canAuthenticate()) Toast.makeText(context, "Set up a screen lock first", Toast.LENGTH_LONG).show()
                    else auth.authenticate(if (lockedNow) "Remove lock" else "Lock list", null) { ok -> if (ok) { vm.setLocked(!lockedNow); unlocked = true } }
                },
                onShare = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, vm.shareText()), null))
                },
                onExport = null, onHistory = null, onInfo = null,
                onCopy = { vm.duplicate { nid -> navigator.back(); navigator.open(nid, true) } },
                onArchive = {
                    val archive = note?.archived != true
                    vm.setArchived(archive); navigator.back()
                    if (archive) container.appScope.launch(Dispatchers.Main) { snackbar.showUndo("Archived") { container.repository.setArchived(listOf(vm.noteId), false) } }
                },
                onDelete = {
                    haptics.confirm()
                    val nid = vm.noteId
                    vm.trash { navigator.back() }
                    container.appScope.launch(Dispatchers.Main) { snackbar.showUndo("Moved to trash") { container.repository.restore(listOf(nid)) } }
                },
            ))
        },
        onDismiss = { options = false },
    )

    if (reminderSheet) {
        val existing = reminders.firstOrNull()
        ReminderSheet(
            initial = existing, defaultTitle = vm.title.ifBlank { "Checklist" }, titleRequired = false,
            onSave = { t, due, rep -> vm.saveReminder(existing, t, due, rep) },
            onDelete = existing?.let { r -> { vm.deleteReminder(r) } },
            onDismiss = { reminderSheet = false },
        )
    }
}

/** Scrolls the list when dragging near its edges; returns the delta to add to the drag offset. */
private fun autoScroll(state: LazyListState, draggingId: Long): Float {
    val layout = state.layoutInfo
    val current = layout.visibleItemsInfo.firstOrNull { it.key == "u-$draggingId" } ?: return 0f
    val edge = 140
    val delta = when {
        current.offset + current.size > layout.viewportEndOffset - edge -> 18f
        current.offset < layout.viewportStartOffset + edge -> -18f
        else -> 0f
    }
    if (delta == 0f) return 0f
    val consumed = state.dispatchRawDelta(delta)
    return consumed
}

@Composable
private fun ItemRow(
    item: ChecklistItemEntity,
    vm: ListEditorViewModel,
    draggable: Boolean,
    modifier: Modifier,
    dragHandle: Modifier,
    onDelete: (ChecklistItemEntity) -> Unit,
) {
    val haptics = rememberHaptics()
    val extra = NC.extra
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var tfv by remember(item.id) { mutableStateOf(TextFieldValue(item.text, TextRange(item.text.length))) }
    if (tfv.text != item.text) tfv = TextFieldValue(item.text, TextRange(item.text.length))

    LaunchedEffect(vm.focusRequest) {
        if (vm.focusRequest == item.id) { focus.requestFocus(); vm.focusRequest = null }
    }
    val check by animateFloatAsState(if (item.checked) 1f else 0f, label = "check")

    Row(modifier.fillMaxWidth().padding(start = 6.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 30.dp, height = 44.dp).then(if (draggable && !item.checked) dragHandle else Modifier), contentAlignment = Alignment.Center) {
            if (draggable && !item.checked) Icon(Icons.Rounded.DragIndicator, "Reorder", tint = extra.faint.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        }
        Box(
            Modifier.size(24.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = check))
                .border(1.8.dp, if (item.checked) MaterialTheme.colorScheme.primary else extra.faint, CircleShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (!item.checked) haptics.confirm() else haptics.tick()
                    vm.toggle(item.id)
                },
            contentAlignment = Alignment.Center
        ) {
            if (item.checked) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp).graphicsLayer { scaleX = check; scaleY = check })
        }
        Spacer(Modifier.width(14.dp))
        BasicTextField(
            value = tfv,
            onValueChange = { v -> tfv = v; if (v.text != item.text) vm.onText(item.id, v.text) },
            textStyle = TextStyle(
                fontSize = (NC.extra.editorFontSize).sp, lineHeight = (NC.extra.editorFontSize * 1.45f).sp, fontFamily = extra.editorFont,
                color = if (item.checked) extra.muted else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { vm.addAfter(item.id) }),
            modifier = Modifier.weight(1f).padding(vertical = 10.dp).focusRequester(focus)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Backspace && tfv.text.isEmpty()) { vm.removeEmpty(item.id); true } else false
                },
            decorationBox = { inner ->
                Box { if (tfv.text.isEmpty()) Text("List item", style = TextStyle(fontSize = NC.extra.editorFontSize.sp, color = extra.faint)); inner() }
            }
        )
        if (focused || item.checked) IconButton(onClick = { haptics.tick(); onDelete(item) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Close, "Delete item", tint = extra.faint, modifier = Modifier.size(18.dp))
        }
    }
}
