package com.notecraft.app.data

import android.net.Uri
import androidx.room.withTransaction
import com.notecraft.app.data.db.AppDatabase
import com.notecraft.app.data.db.AttachmentEntity
import com.notecraft.app.data.db.ChecklistItemEntity
import com.notecraft.app.data.db.NoteEntity
import com.notecraft.app.data.db.NoteKind
import com.notecraft.app.data.db.NoteWithMeta
import com.notecraft.app.data.db.ReminderEntity
import com.notecraft.app.data.db.Repeat
import com.notecraft.app.data.db.RevisionEntity
import com.notecraft.app.data.db.SearchHit
import com.notecraft.app.domain.TextTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

interface ReminderScheduling {
    fun schedule(reminder: ReminderEntity)
    fun cancel(reminderId: Long)
}

data class TagCount(val tag: String, val count: Int)

class NoteRepository(
    private val db: AppDatabase,
    private val attachmentsStore: AttachmentStore,
    private val scheduler: ReminderScheduling,
    private val onChanged: () -> Unit,
) {
    private val notes = db.notes()
    private val items = db.checklist()
    private val reminders = db.reminders()
    private val attachments = db.attachments()
    private val revisions = db.revisions()
    private val activity = db.activity()

    // ---- Queries --------------------------------------------------------------------------

    fun cards(kind: Int): Flow<List<NoteWithMeta>> = notes.observeCards(kind)
    fun archived(): Flow<List<NoteWithMeta>> = notes.observeArchived()
    fun trashed(): Flow<List<NoteWithMeta>> = notes.observeTrashed()
    fun observe(id: Long): Flow<NoteEntity?> = notes.observe(id)
    suspend fun get(id: Long): NoteEntity? = notes.get(id)
    fun titles(): Flow<List<String>> = notes.observeTitles()
    fun activeCount(): Flow<Int> = notes.observeActiveCount()
    suspend fun widgetCards(limit: Int) = notes.widgetCards(limit)

    fun tags(): Flow<List<TagCount>> = notes.observeNoteBodies().map { bodies ->
        val counts = HashMap<String, Pair<String, Int>>()
        for (b in bodies) for (t in TextTools.hashtags(b)) {
            val k = t.lowercase()
            val prev = counts[k]
            counts[k] = (prev?.first ?: t) to ((prev?.second ?: 0) + 1)
        }
        counts.values.map { TagCount(it.first, it.second) }.sortedWith(compareByDescending<TagCount> { it.count }.thenBy { it.tag.lowercase() })
    }.flowOn(Dispatchers.Default)

    suspend fun search(raw: String): List<SearchHit> {
        val tokens = raw.trim().split(Regex("""\s+""")).map { it.replace(Regex("""["*^():]"""), "") }.filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val match = tokens.map { it.trimStart('#') }.filter { it.isNotBlank() }.joinToString(" ") { "\"$it*\"" }
        if (match.isBlank()) return emptyList()
        return runCatching { notes.search(match, HL_START, HL_END) }.getOrElse { emptyList() }
    }

    suspend fun findByTitle(title: String) = notes.findByTitle(title.trim())
    suspend fun backlinks(note: NoteEntity): List<NoteEntity> =
        if (note.title.isBlank()) emptyList() else notes.backlinks("[[${note.title.trim()}", note.id)

    // ---- Notes ----------------------------------------------------------------------------

    suspend fun createNote(title: String = "", body: String = "", kind: Int = NoteKind.NOTE, color: Int = 0): Long {
        val now = System.currentTimeMillis()
        val id = notes.insert(NoteEntity(kind = kind, title = title, body = body, color = color, createdAt = now, updatedAt = now))
        bumpActivity()
        onChanged()
        return id
    }

    suspend fun saveContent(id: Long, title: String, body: String) {
        notes.updateContent(id, title, body, System.currentTimeMillis())
        bumpActivity()
        onChanged()
    }

    suspend fun setPinned(ids: List<Long>, pinned: Boolean) { notes.setPinned(ids, pinned); onChanged() }
    suspend fun setColor(ids: List<Long>, color: Int) { notes.setColor(ids, color); onChanged() }
    suspend fun setArchived(ids: List<Long>, archived: Boolean) { notes.setArchived(ids, archived); onChanged() }
    suspend fun setLocked(id: Long, locked: Boolean) { notes.setLocked(id, locked); onChanged() }

    suspend fun trash(ids: List<Long>) {
        notes.trash(ids, System.currentTimeMillis())
        reminders.forNotes(ids).forEach { scheduler.cancel(it.id) }
        onChanged()
    }

    suspend fun restore(ids: List<Long>) {
        notes.restore(ids)
        reminders.forNotes(ids).filter { !it.done }.forEach { scheduler.schedule(it) }
        onChanged()
    }

    suspend fun deleteForever(ids: List<Long>) {
        if (ids.isEmpty()) return
        val files = attachments.forNotes(ids).map { it.fileName }
        reminders.forNotes(ids).forEach { scheduler.cancel(it.id) }
        notes.deleteForever(ids)
        files.forEach { attachmentsStore.delete(it) }
        onChanged()
    }

    suspend fun purgeExpiredTrash() = deleteForever(notes.expiredTrash(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(TRASH_DAYS)))
    suspend fun emptyTrash() = deleteForever(notes.allTrashed())

    /** Deletes a note silently if the user left it completely empty. */
    suspend fun deleteIfEmpty(id: Long): Boolean {
        val n = notes.get(id) ?: return false
        val empty = n.title.isBlank() && n.body.isBlank() &&
            attachments.forNotes(listOf(id)).isEmpty() && items.get(id).all { it.text.isBlank() }
        if (empty) deleteForever(listOf(id))
        return empty
    }

    suspend fun duplicate(id: Long): Long? = db.withTransaction {
        val n = notes.get(id) ?: return@withTransaction null
        val now = System.currentTimeMillis()
        val newId = notes.insert(n.copy(id = 0, title = if (n.title.isBlank()) "" else "${n.title} (copy)", pinned = false, createdAt = now, updatedAt = now))
        if (n.isList) items.insertAll(items.get(id).map { it.copy(id = 0, noteId = newId) })
        newId
    }.also { onChanged() }

    suspend fun convertToList(id: Long) = db.withTransaction {
        val n = notes.get(id) ?: return@withTransaction
        val lines = TextTools.parseTasks(n.body).takeIf { it.isNotEmpty() }
            ?: TextTools.splitToItems(n.body).map { TextTools.TaskLine(false, it) }
        items.deleteAllFor(id)
        items.insertAll(lines.mapIndexed { i, t -> ChecklistItemEntity(noteId = id, text = TextTools.inlineStrip(t.text), checked = t.checked, position = i) })
        val all = items.get(id)
        notes.update(n.copy(kind = NoteKind.LIST, body = TextTools.checklistBody(all), updatedAt = System.currentTimeMillis()))
    }.also { onChanged() }

    suspend fun convertToNote(id: Long) = db.withTransaction {
        val n = notes.get(id) ?: return@withTransaction
        val body = TextTools.checklistBody(items.get(id))
        items.deleteAllFor(id)
        notes.update(n.copy(kind = NoteKind.NOTE, body = body, updatedAt = System.currentTimeMillis()))
    }.also { onChanged() }

    // ---- Checklists -----------------------------------------------------------------------

    fun observeItems(noteId: Long) = items.observe(noteId)
    suspend fun getItems(noteId: Long) = items.get(noteId)

    suspend fun addItem(noteId: Long, text: String, position: Int, checked: Boolean = false): Long =
        items.insert(ChecklistItemEntity(noteId = noteId, text = text, checked = checked, position = position))

    suspend fun syncChecklist(noteId: Long, title: String, list: List<ChecklistItemEntity>) {
        db.withTransaction {
            val normalized = list.mapIndexed { i, it -> it.copy(position = i, noteId = noteId) }
            items.upsertAll(normalized)
            items.deleteOthers(noteId, normalized.map { it.id }.ifEmpty { listOf(-1L) })
            notes.updateContent(noteId, title, TextTools.checklistBody(normalized), System.currentTimeMillis())
        }
        bumpActivity()
        onChanged()
    }

    // ---- Reminders ------------------------------------------------------------------------

    fun observeReminders() = reminders.observeAll()
    fun observeRemindersFor(noteId: Long) = reminders.observeForNote(noteId)
    suspend fun getReminder(id: Long) = reminders.get(id)

    suspend fun addReminder(title: String, dueAt: Long, repeat: Int, noteId: Long?): Long {
        val r = ReminderEntity(title = title, dueAt = dueAt, repeat = repeat, noteId = noteId)
        val id = reminders.insert(r)
        scheduler.schedule(r.copy(id = id))
        onChanged()
        return id
    }

    suspend fun updateReminder(r: ReminderEntity) {
        reminders.update(r)
        if (r.done) scheduler.cancel(r.id) else scheduler.schedule(r)
        onChanged()
    }

    suspend fun deleteReminder(r: ReminderEntity) {
        scheduler.cancel(r.id)
        reminders.delete(r)
        onChanged()
    }

    suspend fun restoreReminder(r: ReminderEntity) {
        val id = reminders.insert(r.copy(id = 0))
        if (!r.done) scheduler.schedule(r.copy(id = id))
        onChanged()
    }

    /** Marks done; repeating reminders roll forward to their next occurrence instead. */
    suspend fun completeReminder(id: Long) {
        val r = reminders.get(id) ?: return
        if (r.repeat != Repeat.NONE) {
            val next = nextOccurrence(r.dueAt, r.repeat, maxOf(System.currentTimeMillis(), r.dueAt))
            updateReminder(r.copy(dueAt = next))
        } else {
            updateReminder(r.copy(done = true, doneAt = System.currentTimeMillis()))
        }
    }

    suspend fun reopenReminder(id: Long) {
        val r = reminders.get(id) ?: return
        val due = if (r.dueAt < System.currentTimeMillis()) com.notecraft.app.domain.NaturalDate.defaultDue() else r.dueAt
        updateReminder(r.copy(done = false, doneAt = null, dueAt = due))
    }

    suspend fun snoozeReminder(id: Long, minutes: Long) {
        val r = reminders.get(id) ?: return
        updateReminder(r.copy(dueAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes), done = false))
    }

    /** Called when an alarm fires. Repeating reminders schedule their next occurrence. */
    suspend fun onReminderFired(id: Long): ReminderEntity? {
        val r = reminders.get(id) ?: return null
        if (r.repeat != Repeat.NONE) {
            val next = nextOccurrence(r.dueAt, r.repeat, System.currentTimeMillis())
            updateReminder(r.copy(dueAt = next))
        }
        return r
    }

    suspend fun clearCompletedReminders() { reminders.clearCompleted(); onChanged() }

    suspend fun rescheduleAll() {
        val trashedIds = notes.allTrashed().toSet()
        reminders.pending().filter { it.noteId == null || it.noteId !in trashedIds }.forEach { scheduler.schedule(it) }
    }

    // ---- Attachments ----------------------------------------------------------------------

    fun observeAttachments(noteId: Long) = attachments.observe(noteId)

    suspend fun addAttachment(noteId: Long, uri: Uri): Boolean {
        val imported = attachmentsStore.import(uri) ?: return false
        attachments.insert(AttachmentEntity(noteId = noteId, fileName = imported.fileName, width = imported.width, height = imported.height))
        notes.get(noteId)?.let { notes.update(it.copy(updatedAt = System.currentTimeMillis())) }
        onChanged()
        return true
    }

    suspend fun removeAttachment(a: AttachmentEntity) {
        attachments.delete(a)
        attachmentsStore.delete(a.fileName)
        onChanged()
    }

    // ---- Revisions & activity -------------------------------------------------------------

    fun observeRevisions(noteId: Long) = revisions.observe(noteId)

    suspend fun addRevision(noteId: Long, title: String, body: String) {
        if (title.isBlank() && body.isBlank()) return
        revisions.insert(RevisionEntity(noteId = noteId, title = title, body = body, savedAt = System.currentTimeMillis()))
        revisions.trim(noteId, MAX_REVISIONS)
    }

    fun observeActivity() = activity.observeAll()

    private suspend fun bumpActivity() = withContext(Dispatchers.IO) {
        activity.bump(LocalDate.now().toEpochDay())
    }

    companion object {
        const val HL_START = "\u0001"
        const val HL_END = "\u0002"
        const val TRASH_DAYS = 30L
        const val MAX_REVISIONS = 30

        fun nextOccurrence(from: Long, repeat: Int, after: Long): Long {
            val zone = ZoneId.systemDefault()
            var dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(from), zone)
            var guard = 0
            do {
                dt = when (repeat) {
                    Repeat.DAILY -> dt.plusDays(1)
                    Repeat.WEEKDAYS -> {
                        var n = dt.plusDays(1)
                        while (n.dayOfWeek == DayOfWeek.SATURDAY || n.dayOfWeek == DayOfWeek.SUNDAY) n = n.plusDays(1)
                        n
                    }
                    Repeat.WEEKLY -> dt.plusWeeks(1)
                    Repeat.MONTHLY -> dt.plusMonths(1)
                    Repeat.YEARLY -> dt.plusYears(1)
                    else -> return from
                }
                guard++
            } while (dt.atZone(zone).toInstant().toEpochMilli() <= after && guard < 5000)
            return dt.atZone(zone).toInstant().toEpochMilli()
        }
    }
}
