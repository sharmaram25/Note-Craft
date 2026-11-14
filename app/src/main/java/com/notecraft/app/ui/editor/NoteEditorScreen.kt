package com.notecraft.app.ui.editor

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notecraft.app.domain.TextTools
import com.notecraft.app.ui.LocalAuth
import com.notecraft.app.ui.LocalNavigator
import com.notecraft.app.ui.common.Fmt
import com.notecraft.app.ui.common.LocalContainer
import com.notecraft.app.ui.common.LocalSnackbar
import com.notecraft.app.ui.common.appViewModel
import com.notecraft.app.ui.common.rememberHaptics
import com.notecraft.app.ui.common.sharedNote
import com.notecraft.app.ui.common.showUndo
import com.notecraft.app.ui.reminders.ReminderSheet
import com.notecraft.app.ui.theme.NC
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Sheet { NONE, OPTIONS, INFO, HISTORY, REMINDER }

@Composable
fun NoteEditorScreen(id: Long, origin: String?) {
    val vm = appViewModel(key = "note-$id") { NoteEditorViewModel(it, id) }
    val container = LocalContainer.current
    val navigator = LocalNavigator.current
    val snackbar = LocalSnackbar.current
    val auth = LocalAuth.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val settings by container.settings.settings.collectAsState(initial = null)
    val note by vm.note.collectAsStateWithLifecycle()
    val attachments by vm.attachments.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val revisions by vm.revisions.collectAsStateWithLifecycle()
    val titles by vm.titles.collectAsStateWithLifecycle()

    val extra = NC.extra
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val titleFocus = remember { FocusRequester() }
    val bodyFocus = remember { FocusRequester() }
    var bodyFocused by remember { mutableStateOf(false) }
    var titleFocused by remember { mutableStateOf(false) }
    var readMode by rememberSaveable { mutableStateOf(false) }
    var readModeInitialized by rememberSaveable { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    var viewing by remember { mutableStateOf<com.notecraft.app.data.db.AttachmentEntity?>(null) }
    var unlocked by remember { mutableStateOf(id <= 0 || vm.isUnlocked(id)) }
    val isNew = vm.noteId <= 0
    val imeVisible = WindowInsets.isImeVisible

    // Initial mode & focus.
    LaunchedEffect(vm.loaded, settings) {
        val s = settings ?: return@LaunchedEffect
        if (!vm.loaded || readModeInitialized) return@LaunchedEffect
        readModeInitialized = true
        if (id <= 0) bodyFocus.requestFocus()
        else readMode = s.openInReadMode && vm.body.text.isNotBlank()
    }
    // Save when the app goes to the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) vm.saveInBackground() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val locked = note?.locked == true && !unlocked
    fun promptUnlock() = auth.authenticate("Unlock note", note?.title?.takeIf { it.isNotBlank() }) { ok ->
        if (ok) { vm.markUnlocked(vm.noteId); unlocked = true }
    }
    LaunchedEffect(note?.locked) { if (note?.locked == true && !unlocked) promptUnlock() }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.addImage(it) { Toast.makeText(context, "Couldn't add that image", Toast.LENGTH_SHORT).show() } }
    }
    val exportMd = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { os ->
                        os.write(buildString { if (vm.title.isNotBlank()) append("# ${vm.title.trim()}\n\n"); append(vm.body.text) }.toByteArray())
                    }
                }.isSuccess
            }
            Toast.makeText(context, if (ok) "Exported" else "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    val bg by animateColorAsState(
        if ((note?.color ?: 0) == 0) MaterialTheme.colorScheme.background else NC.noteBg(note!!.color),
        tween(300), label = "bg"
    )
    val fontSize = settings?.editorFontSize ?: 17
    val palette = MdPalette(
        text = MaterialTheme.colorScheme.onSurface, syntax = extra.faint, accent = MaterialTheme.colorScheme.primary,
        muted = extra.muted, codeBg = extra.codeBg, highlight = extra.highlight, baseSize = fontSize.sp,
    )
    val bodyStyle = TextStyle(
        fontFamily = extra.editorFont, fontSize = fontSize.sp, lineHeight = (fontSize * 1.6f).sp,
        color = MaterialTheme.colorScheme.onSurface, letterSpacing = 0.1.sp,
    )
    val transformation = remember(palette) { MarkdownVisualTransformation(palette) }

    // [[wiki-link]] autocomplete context.
    val wiki = remember(vm.body) {
        val v = vm.body
        if (!v.selection.collapsed) null else {
            val before = v.text.substring(0, v.selection.start)
            val open = before.lastIndexOf("[[")
            if (open < 0 || before.indexOf("]]", open) >= 0 || before.indexOf('\n', open) >= 0) null
            else (open + 2) to before.substring(open + 2)
        }
    }
    val suggestions = remember(wiki, titles) {
        wiki?.let { (_, q) -> titles.filter { it.contains(q, true) && !it.equals(vm.title, true) }.distinct().take(8) } ?: emptyList()
    }

    fun openWiki(target: String) = scope.launch {
        val n = vm.resolveWiki(target)
        if (n != null) navigator.open(n.id, n.isList) else navigator.open(vm.createLinked(target), false)
    }

    Box(
        Modifier.fillMaxSize().sharedNote(origin).background(bg)
    ) {
        Column(Modifier.fillMaxSize().imePadding()) {
            // ---- Top bar
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = navigator::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Spacer(Modifier.weight(1f))
                if (!locked) {
                    IconButton(onClick = {
                        haptics.tick()
                        readMode = !readMode
                        if (readMode) { focusManager.clearFocus(); keyboard?.hide() }
                    }) {
                        Icon(if (readMode) Icons.Rounded.EditNote else Icons.Outlined.AutoStories, if (readMode) "Edit" else "Reading mode", tint = extra.muted)
                    }
                    IconButton(onClick = { haptics.confirm(); vm.setPinned(!(note?.pinned ?: false)) }) {
                        Icon(
                            if (note?.pinned == true) Icons.Rounded.PushPin else Icons.Outlined.PushPin, "Pin",
                            tint = if (note?.pinned == true) MaterialTheme.colorScheme.primary else extra.muted
                        )
                    }
                    IconButton(onClick = { sheet = Sheet.REMINDER }) {
                        Icon(
                            if (reminders.isNotEmpty()) Icons.Rounded.Alarm else Icons.Outlined.Alarm, "Reminder",
                            tint = if (reminders.isNotEmpty()) MaterialTheme.colorScheme.primary else extra.muted
                        )
                    }
                }
                IconButton(onClick = { focusManager.clearFocus(); sheet = Sheet.OPTIONS }) { Icon(Icons.Rounded.MoreVert, "More options", tint = extra.muted) }
            }

            Box(Modifier.weight(1f)) {
                if (locked) LockedPlaceholder(::promptUnlock)
                else AnimatedContent(readMode, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "mode") { reading ->
                    val scroll = rememberScrollState()
                    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                        if (attachments.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            AttachmentStrip(attachments) { viewing = it }
                            Spacer(Modifier.height(12.dp))
                        }
                        reminders.firstOrNull()?.let { r ->
                            ReminderChip(r.dueAt, r.repeat) { sheet = Sheet.REMINDER }
                        }
                        if (reading) {
                            if (vm.title.isNotBlank()) Text(
                                vm.title, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = extra.editorFont),
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                            )
                            ReadView(
                                body = vm.body.text, palette = palette, textStyle = bodyStyle,
                                onToggleTask = vm::toggleTaskLine, onWiki = { openWiki(it) }, onTag = { navigator.search(it) },
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                            )
                            Spacer(Modifier.height(160.dp))
                        } else {
                            BasicTextField(
                                value = vm.title,
                                onValueChange = { t ->
                                    if ('\n' in t) { vm.onTitle(t.replace("\n", "")); bodyFocus.requestFocus() } else vm.onTitle(t)
                                },
                                textStyle = MaterialTheme.typography.headlineSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface, fontFamily = extra.editorFont, fontWeight = FontWeight.SemiBold
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { bodyFocus.requestFocus() }),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)
                                    .focusRequester(titleFocus).onFocusChanged { titleFocused = it.isFocused },
                                decorationBox = { inner ->
                                    Box {
                                        if (vm.title.isEmpty()) Text("Title", style = MaterialTheme.typography.headlineSmall.copy(color = extra.faint, fontFamily = extra.editorFont, fontWeight = FontWeight.SemiBold))
                                        inner()
                                    }
                                }
                            )
                            BasicTextField(
                                value = vm.body,
                                onValueChange = vm::onBody,
                                textStyle = bodyStyle,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                visualTransformation = transformation,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)
                                    .heightIn(min = 320.dp)
                                    .focusRequester(bodyFocus).onFocusChanged { bodyFocused = it.isFocused },
                                decorationBox = { inner ->
                                    Box {
                                        if (vm.body.text.isEmpty()) Text("Start writing…", style = bodyStyle.copy(color = extra.faint))
                                        inner()
                                    }
                                }
                            )
                            // Tapping the empty space below the text continues writing at the end.
                            Box(
                                Modifier.fillMaxWidth().height(220.dp).clickable(
                                    interactionSource = remember { MutableInteractionSource() }, indication = null
                                ) {
                                    vm.apply { TextFieldValue(it.text, TextRange(it.text.length)) }
                                    bodyFocus.requestFocus()
                                    keyboard?.show()
                                }
                            )
                        }
                    }
                }
            }

            // ---- Bottom: toolbar while typing, quiet status line otherwise.
            val editing = !readMode && !locked && imeVisible && (bodyFocused || titleFocused)
            AnimatedVisibility(editing && suggestions.isNotEmpty() || (editing && wiki != null && wiki.second.isNotBlank())) {
                WikiSuggestions(suggestions, wiki?.second.orEmpty()) { t -> wiki?.let { vm.completeWikiLink(it.first, t) } }
            }
            if (editing) {
                FormatToolbar(
                    canUndo = vm.canUndo, canRedo = vm.canRedo,
                    onUndo = vm::undo, onRedo = vm::redo,
                    onOp = { op -> haptics.tick(); vm.apply(op) },
                    onImage = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onHideKeyboard = { focusManager.clearFocus(); keyboard?.hide() },
                    titleMode = titleFocused,
                )
            } else if (!locked) {
                val words = remember(vm.body.text, vm.title) { TextTools.wordCount(vm.title + " " + vm.body.text) }
                Text(
                    buildString {
                        note?.let { append("Edited ${Fmt.relative(it.updatedAt).replaceFirstChar { c -> c.lowercase() }}") } ?: append("New note")
                        append("  ·  $words ${if (words == 1) "word" else "words"}")
                    },
                    style = MaterialTheme.typography.labelSmall, color = extra.faint,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 10.dp)
                )
            }
        }
    }

    // ---- Sheets & dialogs
    when (sheet) {
        Sheet.OPTIONS -> OptionsSheet(
            color = note?.color ?: 0, onColor = { haptics.tick(); vm.setColor(it) },
            actions = NoteActions.build(
                isNew = isNew, note = note, isList = false, hasReminder = reminders.isNotEmpty(),
                onImage = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onReminder = { sheet = Sheet.REMINDER },
                onConvert = { vm.convertToList { nid -> navigator.back(); navigator.open(nid, true) } },
                onLock = {
                    val lockedNow = note?.locked == true
                    if (!auth.canAuthenticate()) Toast.makeText(context, "Set up a screen lock first", Toast.LENGTH_LONG).show()
                    else auth.authenticate(if (lockedNow) "Remove lock" else "Lock note", null) { ok -> if (ok) { vm.setLocked(!lockedNow); unlocked = true } }
                },
                onShare = {
                    val text = buildString { if (vm.title.isNotBlank()) append(vm.title.trim()).append("\n\n"); append(vm.body.text) }
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, vm.title).putExtra(Intent.EXTRA_TEXT, text), null))
                },
                onExport = { exportMd.launch(TextTools.safeFileName(vm.title, "Note") + ".md") },
                onHistory = { sheet = Sheet.HISTORY },
                onInfo = { sheet = Sheet.INFO },
                onCopy = { vm.duplicate { nid -> navigator.back(); navigator.open(nid, false) } },
                onArchive = {
                    val archive = note?.archived != true
                    vm.setArchived(archive); navigator.back()
                    if (archive) container.appScope.launch(Dispatchers.Main) { snackbar.showUndo("Archived") { container.repository.setArchived(listOf(vm.noteId), false) } }
                },
                onDelete = {
                    haptics.confirm()
                    val nid = vm.noteId
                    vm.trash { navigator.back() }
                    if (nid > 0) container.appScope.launch(Dispatchers.Main) { snackbar.showUndo("Moved to trash") { container.repository.restore(listOf(nid)) } }
                },
            ),
            onDismiss = { if (sheet == Sheet.OPTIONS) sheet = Sheet.NONE },
        )
        Sheet.INFO -> InfoSheet(note, vm.title, vm.body.text, vm::backlinks, { n -> navigator.open(n.id, n.isList) }) { sheet = Sheet.NONE }
        Sheet.HISTORY -> RevisionsSheet(revisions, vm::restoreRevision) { sheet = Sheet.NONE }
        Sheet.REMINDER -> {
            val existing = reminders.firstOrNull()
            ReminderSheet(
                initial = existing,
                defaultTitle = vm.title.ifBlank { TextTools.plainPreview(vm.body.text, 60).lineSequence().firstOrNull().orEmpty() }.ifBlank { "Note reminder" },
                titleRequired = false,
                onSave = { t, due, rep -> vm.saveReminder(existing, t, due, rep) },
                onDelete = existing?.let { r -> { vm.deleteReminder(r) } },
                onDismiss = { sheet = Sheet.NONE },
            )
        }
        Sheet.NONE -> {}
    }
    viewing?.let { a -> ImageViewer(a, onDelete = { vm.removeImage(a) }, onDismiss = { viewing = null }) }
}


@Composable
private fun ReminderChip(due: Long, repeat: Int, onClick: () -> Unit) {
    val overdue = due < System.currentTimeMillis()
    val tint = if (overdue) NC.extra.danger else MaterialTheme.colorScheme.primary
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            .background(tint.copy(alpha = 0.1f), MaterialTheme.shapes.small)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Alarm, null, tint = tint, modifier = Modifier.height(14.dp))
        Spacer(Modifier.padding(start = 6.dp))
        Text(
            Fmt.due(due) + if (repeat != 0) " · ${com.notecraft.app.ui.reminders.repeatLabel(repeat)}" else "",
            style = MaterialTheme.typography.labelMedium, color = tint
        )
    }
}
