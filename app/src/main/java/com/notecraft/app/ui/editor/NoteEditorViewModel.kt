package com.notecraft.app.ui.editor

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notecraft.app.AppContainer
import com.notecraft.app.data.db.NoteEntity
import com.notecraft.app.data.db.ReminderEntity
import com.notecraft.app.data.db.RevisionEntity
import com.notecraft.app.domain.TextTools
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class NoteEditorViewModel(private val c: AppContainer, initialId: Long) : ViewModel() {
    private val repo = c.repository

    var noteId by mutableLongStateOf(initialId)
        private set
    var title by mutableStateOf("")
        private set
    var body by mutableStateOf(TextFieldValue(""))
        private set
    var loaded by mutableStateOf(initialId <= 0)
        private set
    var historyVersion by mutableIntStateOf(0)
        private set

    private val history = UndoHistory()
    val canUndo get() = historyVersion >= 0 && history.canUndo
    val canRedo get() = historyVersion >= 0 && history.canRedo

    private var original: Pair<String, String>? = null
    private var dirty = false
    private var saveJob: Job? = null
    private val saveMutex = Mutex()

    private val idFlow = snapshotFlow { noteId }
    val note: StateFlow<NoteEntity?> = idFlow.flatMapLatest { if (it > 0) repo.observe(it) else flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val attachments = idFlow.flatMapLatest { if (it > 0) repo.observeAttachments(it) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val reminders: StateFlow<List<ReminderEntity>> = idFlow.flatMapLatest { if (it > 0) repo.observeRemindersFor(it) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val revisions: StateFlow<List<RevisionEntity>> = idFlow.flatMapLatest { if (it > 0) repo.observeRevisions(it) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val titles = repo.titles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (initialId > 0) viewModelScope.launch {
            repo.get(initialId)?.let { n ->
                title = n.title
                body = TextFieldValue(n.body, TextRange(n.body.length))
                original = n.title to n.body
            }
            history.reset(body)
            loaded = true
        }
    }

    // ---- Editing --------------------------------------------------------------------------

    fun onTitle(t: String) {
        if (t == title) return
        title = t
        scheduleSave()
    }

    fun onBody(new: TextFieldValue) {
        val processed = EditorOps.onTextChange(body, new)
        if (processed.text != body.text) {
            history.record(body, processed)
            historyVersion++
            body = processed
            scheduleSave()
        } else body = processed
    }

    fun apply(transform: (TextFieldValue) -> TextFieldValue) {
        val next = transform(body)
        if (next.text == body.text && next.selection == body.selection) return
        if (next.text != body.text) { history.record(body, next); historyVersion++; scheduleSave() }
        body = next
    }

    fun undo() { history.undo(body)?.let { body = it; historyVersion++; scheduleSave() } }
    fun redo() { history.redo(body)?.let { body = it; historyVersion++; scheduleSave() } }

    fun toggleTaskLine(line: Int) = apply { TextFieldValue(TextTools.toggleTaskAtLine(it.text, line), it.selection) }

    fun completeWikiLink(partialStart: Int, titleToInsert: String) = apply { v ->
        val t = v.text
        val cur = v.selection.start
        val insert = "$titleToInsert]]"
        val nt = t.substring(0, partialStart) + insert + t.substring(cur)
        TextFieldValue(nt, TextRange(partialStart + insert.length))
    }

    // ---- Persistence ----------------------------------------------------------------------

    private fun scheduleSave() {
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(450); saveNow() }
    }

    suspend fun saveNow() = saveMutex.withLock {
        if (!dirty) return@withLock
        dirty = false
        val t = title; val b = body.text
        if (noteId <= 0) {
            if (t.isBlank() && b.isBlank()) return@withLock
            noteId = repo.createNote(t, b)
            original = "" to ""
        } else repo.saveContent(noteId, t, b)
    }

    /** Ensures the note exists in the database (for actions on a brand-new note). */
    suspend fun ensureId(): Long = saveMutex.withLock {
        if (noteId <= 0) {
            noteId = repo.createNote(title, body.text)
            original = "" to ""
            dirty = false
        }
        noteId
    }

    fun saveInBackground() { c.appScope.launch { withContext(NonCancellable) { saveNow() } } }

    override fun onCleared() {
        val t = title; val b = body.text; val orig = original
        c.appScope.launch {
            saveNow()
            val id = noteId
            if (id > 0 && !repo.deleteIfEmpty(id)) {
                if (orig != null && (orig.first.isNotBlank() || orig.second.isNotBlank()) && (orig.first != t || orig.second != b)) {
                    repo.addRevision(id, orig.first, orig.second)
                }
            }
        }
    }

    // ---- Actions --------------------------------------------------------------------------

    fun setPinned(v: Boolean) = viewModelScope.launch { repo.setPinned(listOf(ensureId()), v) }
    fun setColor(v: Int) = viewModelScope.launch { repo.setColor(listOf(ensureId()), v) }
    fun setArchived(v: Boolean) = viewModelScope.launch { repo.setArchived(listOf(ensureId()), v) }
    /** Locking keeps the note open for this session; it locks next time the app is backgrounded. */
    fun setLocked(v: Boolean) = viewModelScope.launch { val id = ensureId(); repo.setLocked(id, v); c.lock.markNoteUnlocked(id) }
    fun trash(onDone: () -> Unit) = viewModelScope.launch { saveNow(); if (noteId > 0) repo.trash(listOf(noteId)); onDone() }
    fun duplicate(onDone: (Long) -> Unit) = viewModelScope.launch { saveNow(); repo.duplicate(ensureId())?.let(onDone) }
    fun convertToList(onDone: (Long) -> Unit) = viewModelScope.launch { saveNow(); val id = ensureId(); repo.convertToList(id); onDone(id) }

    fun saveReminder(existing: ReminderEntity?, title: String, dueAt: Long, repeat: Int) = viewModelScope.launch {
        if (existing != null) repo.updateReminder(existing.copy(title = title, dueAt = dueAt, repeat = repeat, done = false))
        else repo.addReminder(title, dueAt, repeat, ensureId())
    }
    fun deleteReminder(r: ReminderEntity) = viewModelScope.launch { repo.deleteReminder(r) }

    fun addImage(uri: Uri, onFail: () -> Unit) = viewModelScope.launch { if (!repo.addAttachment(ensureId(), uri)) onFail() }
    fun removeImage(a: com.notecraft.app.data.db.AttachmentEntity) = viewModelScope.launch { repo.removeAttachment(a) }

    fun restoreRevision(r: RevisionEntity) = viewModelScope.launch {
        saveNow()
        if (noteId > 0) repo.addRevision(noteId, title, body.text)
        history.record(body, TextFieldValue(r.body)); historyVersion++
        title = r.title
        body = TextFieldValue(r.body, TextRange(r.body.length))
        original = r.title to r.body
        dirty = true
        saveNow()
    }

    suspend fun resolveWiki(target: String): NoteEntity? = repo.findByTitle(target)
    suspend fun createLinked(target: String): Long = repo.createNote(title = target)
    suspend fun backlinks(): List<NoteEntity> = note.value?.let { repo.backlinks(it) } ?: emptyList()

    fun isUnlocked(id: Long) = c.lock.isNoteUnlocked(id)
    fun markUnlocked(id: Long) = c.lock.markNoteUnlocked(id)
}
