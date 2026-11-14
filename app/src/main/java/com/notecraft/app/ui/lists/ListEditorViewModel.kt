package com.notecraft.app.ui.lists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notecraft.app.AppContainer
import com.notecraft.app.data.db.ChecklistItemEntity
import com.notecraft.app.data.db.NoteEntity
import com.notecraft.app.data.db.NoteKind
import com.notecraft.app.data.db.ReminderEntity
import com.notecraft.app.domain.TextTools
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ListEditorViewModel(private val c: AppContainer, initialId: Long) : ViewModel() {
    private val repo = c.repository

    var noteId by mutableLongStateOf(initialId)
        private set
    var title by mutableStateOf("")
        private set
    val items = mutableStateListOf<ChecklistItemEntity>()
    var loaded by mutableStateOf(false)
        private set
    /** Item that should take focus once composed (after Enter / add). */
    var focusRequest by mutableStateOf<Long?>(null)
    var focusTitle by mutableStateOf(false)

    private var syncJob: Job? = null
    private val mutex = Mutex()
    private var dirty = false

    val note: StateFlow<NoteEntity?> = snapshotFlow { noteId }.filter { it > 0 }.flatMapLatest { repo.observe(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val reminders: StateFlow<List<ReminderEntity>> = snapshotFlow { noteId }.flatMapLatest { if (it > 0) repo.observeRemindersFor(it) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            if (noteId <= 0) {
                noteId = repo.createNote(kind = NoteKind.LIST)
                val first = repo.addItem(noteId, "", 0)
                items += ChecklistItemEntity(id = first, noteId = noteId, text = "", position = 0)
                focusTitle = true
            } else {
                repo.get(noteId)?.let { title = it.title }
                items += repo.getItems(noteId)
            }
            loaded = true
        }
    }

    val doneCount get() = items.count { it.checked && it.text.isNotBlank() }
    val totalCount get() = items.count { it.text.isNotBlank() }

    fun onTitle(t: String) { title = t; schedule() }

    fun onText(id: Long, text: String) {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return
        if ('\n' in text) {
            // Enter or a multi-line paste: split into separate items.
            val parts = text.split('\n')
            items[i] = items[i].copy(text = parts.first())
            val rest = parts.drop(1).let { if (parts.size > 2) it.map { p -> TextTools.splitToItems(p).firstOrNull() ?: p.trim() }.filter { p -> p.isNotEmpty() } else it }
            insertAfter(id, rest.ifEmpty { listOf("") })
        } else {
            items[i] = items[i].copy(text = text)
        }
        schedule()
    }

    fun toggle(id: Long) {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return
        items[i] = items[i].copy(checked = !items[i].checked)
        schedule()
    }

    fun addAfter(id: Long?) = insertAfter(id, listOf(""))

    fun addAtEnd() {
        val lastUnchecked = items.lastOrNull { !it.checked }
        insertAfter(lastUnchecked?.id, listOf(""), atEndIfNull = true)
    }

    private fun insertAfter(id: Long?, texts: List<String>, atEndIfNull: Boolean = false) {
        val nid = noteId
        if (nid <= 0) return
        viewModelScope.launch {
            mutex.withLock {
                var at = if (id == null) (if (atEndIfNull) items.indexOfLast { !it.checked } + 1 else 0) else items.indexOfFirst { it.id == id } + 1
                var lastId: Long? = null
                for (t in texts) {
                    val newId = repo.addItem(nid, t, at)
                    items.add(at.coerceIn(0, items.size), ChecklistItemEntity(id = newId, noteId = nid, text = t, position = at))
                    lastId = newId
                    at++
                }
                focusRequest = lastId
            }
            schedule()
        }
    }

    /** Backspace on an empty item: remove it and move focus to the previous one. */
    fun removeEmpty(id: Long) {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0 || items.size <= 1) return
        val prev = items.subList(0, i).lastOrNull { !it.checked }
        items.removeAt(i)
        focusRequest = prev?.id
        schedule()
    }

    fun delete(id: Long) { items.removeAll { it.id == id }; schedule() }

    fun restoreItem(item: ChecklistItemEntity, index: Int) {
        viewModelScope.launch {
            val newId = repo.addItem(noteId, item.text, index, item.checked)
            items.add(index.coerceIn(0, items.size), item.copy(id = newId))
            schedule()
        }
    }

    fun move(fromId: Long, toId: Long) {
        val from = items.indexOfFirst { it.id == fromId }
        val to = items.indexOfFirst { it.id == toId }
        if (from < 0 || to < 0 || from == to) return
        items.add(to, items.removeAt(from))
        schedule()
    }

    fun uncheckAll() { for (i in items.indices) if (items[i].checked) items[i] = items[i].copy(checked = false); schedule() }
    fun checkAll() { for (i in items.indices) if (!items[i].checked && items[i].text.isNotBlank()) items[i] = items[i].copy(checked = true); schedule() }
    fun deleteChecked(): List<ChecklistItemEntity> {
        val removed = items.filter { it.checked }
        items.removeAll { it.checked }
        schedule()
        return removed
    }
    fun restoreMany(list: List<ChecklistItemEntity>) = viewModelScope.launch {
        list.forEach { item -> val newId = repo.addItem(noteId, item.text, items.size, item.checked); items += item.copy(id = newId) }
        schedule()
    }

    private fun schedule() {
        dirty = true
        syncJob?.cancel()
        syncJob = viewModelScope.launch { delay(350); syncNow() }
    }

    suspend fun syncNow() = mutex.withLock {
        if (!dirty || noteId <= 0) return@withLock
        dirty = false
        repo.syncChecklist(noteId, title, items.toList())
    }

    fun syncInBackground() { c.appScope.launch { syncNow() } }

    override fun onCleared() {
        c.appScope.launch {
            syncNow()
            if (noteId > 0) repo.deleteIfEmpty(noteId)
        }
    }

    // ---- Note-level actions ----------------------------------------------------------------
    fun setPinned(v: Boolean) = viewModelScope.launch { repo.setPinned(listOf(noteId), v) }
    fun setColor(v: Int) = viewModelScope.launch { repo.setColor(listOf(noteId), v) }
    fun setArchived(v: Boolean) = viewModelScope.launch { repo.setArchived(listOf(noteId), v) }
    fun setLocked(v: Boolean) = viewModelScope.launch { repo.setLocked(noteId, v); c.lock.markNoteUnlocked(noteId) }
    fun trash(onDone: () -> Unit) = viewModelScope.launch { syncNow(); repo.trash(listOf(noteId)); onDone() }
    fun duplicate(onDone: (Long) -> Unit) = viewModelScope.launch { syncNow(); repo.duplicate(noteId)?.let(onDone) }
    fun convertToNote(onDone: (Long) -> Unit) = viewModelScope.launch { syncNow(); repo.convertToNote(noteId); onDone(noteId) }
    fun saveReminder(existing: ReminderEntity?, t: String, due: Long, rep: Int) = viewModelScope.launch {
        if (existing != null) repo.updateReminder(existing.copy(title = t, dueAt = due, repeat = rep, done = false))
        else repo.addReminder(t, due, rep, noteId)
    }
    fun deleteReminder(r: ReminderEntity) = viewModelScope.launch { repo.deleteReminder(r) }
    fun isUnlocked() = c.lock.isNoteUnlocked(noteId)
    fun markUnlocked() = c.lock.markNoteUnlocked(noteId)

    fun shareText(): String = buildString {
        if (title.isNotBlank()) append(title.trim()).append("\n\n")
        items.filter { it.text.isNotBlank() }.sortedBy { it.checked }.forEach { append(if (it.checked) "☑ " else "☐ ").append(it.text.trim()).append('\n') }
    }.trim()
}
