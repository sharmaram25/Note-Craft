package com.notecraft.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val CARD_COLUMNS = """
    n.*,
    (SELECT COUNT(*) FROM checklist_items c WHERE c.noteId = n.id) AS itemCount,
    (SELECT COUNT(*) FROM checklist_items c WHERE c.noteId = n.id AND c.checked = 1) AS checkedCount,
    (SELECT MIN(r.dueAt) FROM reminders r WHERE r.noteId = n.id AND r.done = 0) AS nextReminder,
    (SELECT a.fileName FROM attachments a WHERE a.noteId = n.id ORDER BY a.id LIMIT 1) AS coverFile,
    (SELECT COUNT(*) FROM attachments a WHERE a.noteId = n.id) AS attachmentCount
"""

@Dao
interface NoteDao {

    @Query("SELECT $CARD_COLUMNS FROM notes n WHERE n.trashedAt IS NULL AND n.archived = 0 AND n.kind = :kind")
    fun observeCards(kind: Int): Flow<List<NoteWithMeta>>

    @Query("SELECT $CARD_COLUMNS FROM notes n WHERE n.trashedAt IS NULL AND n.archived = 1 ORDER BY n.updatedAt DESC")
    fun observeArchived(): Flow<List<NoteWithMeta>>

    @Query("SELECT $CARD_COLUMNS FROM notes n WHERE n.trashedAt IS NOT NULL ORDER BY n.trashedAt DESC")
    fun observeTrashed(): Flow<List<NoteWithMeta>>

    @Query("SELECT $CARD_COLUMNS FROM notes n WHERE n.trashedAt IS NULL AND n.archived = 0 AND n.locked = 0 ORDER BY n.pinned DESC, n.updatedAt DESC LIMIT :limit")
    suspend fun widgetCards(limit: Int): List<NoteWithMeta>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observe(id: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): NoteEntity?

    @Query("SELECT * FROM notes")
    suspend fun all(): List<NoteEntity>

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("UPDATE notes SET title = :title, body = :body, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateContent(id: Long, title: String, body: String, updatedAt: Long)

    @Query("UPDATE notes SET body = :body, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateBody(id: Long, body: String, updatedAt: Long)

    @Query("UPDATE notes SET pinned = :pinned WHERE id IN (:ids)")
    suspend fun setPinned(ids: List<Long>, pinned: Boolean)

    @Query("UPDATE notes SET color = :color WHERE id IN (:ids)")
    suspend fun setColor(ids: List<Long>, color: Int)

    @Query("UPDATE notes SET archived = :archived, pinned = CASE WHEN :archived THEN 0 ELSE pinned END WHERE id IN (:ids)")
    suspend fun setArchived(ids: List<Long>, archived: Boolean)

    @Query("UPDATE notes SET locked = :locked WHERE id = :id")
    suspend fun setLocked(id: Long, locked: Boolean)

    @Query("UPDATE notes SET trashedAt = :at, pinned = 0 WHERE id IN (:ids)")
    suspend fun trash(ids: List<Long>, at: Long)

    @Query("UPDATE notes SET trashedAt = NULL WHERE id IN (:ids)")
    suspend fun restore(ids: List<Long>)

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun deleteForever(ids: List<Long>)

    @Query("SELECT id FROM notes WHERE trashedAt IS NOT NULL AND trashedAt < :before")
    suspend fun expiredTrash(before: Long): List<Long>

    @Query("SELECT id FROM notes WHERE trashedAt IS NOT NULL")
    suspend fun allTrashed(): List<Long>

    @Query(
        """
        SELECT notes.*, snippet(notes_fts, :hlStart, :hlEnd, '…', -1, 12) AS snip
        FROM notes JOIN notes_fts ON notes.id = notes_fts.rowid
        WHERE notes_fts MATCH :query AND notes.trashedAt IS NULL
        ORDER BY notes.pinned DESC, notes.updatedAt DESC
        LIMIT 120
        """
    )
    suspend fun search(query: String, hlStart: String, hlEnd: String): List<SearchHit>

    @Query("SELECT * FROM notes WHERE title = :title COLLATE NOCASE AND trashedAt IS NULL ORDER BY updatedAt DESC LIMIT 1")
    suspend fun findByTitle(title: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE trashedAt IS NULL AND id != :selfId AND body LIKE '%' || :needle || '%' ORDER BY updatedAt DESC")
    suspend fun backlinks(needle: String, selfId: Long): List<NoteEntity>

    @Query("SELECT title FROM notes WHERE trashedAt IS NULL AND title != '' ORDER BY updatedAt DESC")
    fun observeTitles(): Flow<List<String>>

    @Query("SELECT body FROM notes WHERE trashedAt IS NULL AND kind = 0")
    fun observeNoteBodies(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM notes WHERE trashedAt IS NULL AND archived = 0")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun countAll(): Int
}

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklist_items WHERE noteId = :noteId ORDER BY position ASC, id ASC")
    fun observe(noteId: Long): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklist_items WHERE noteId = :noteId ORDER BY position ASC, id ASC")
    suspend fun get(noteId: Long): List<ChecklistItemEntity>

    @Query("SELECT * FROM checklist_items")
    suspend fun all(): List<ChecklistItemEntity>

    @Insert
    suspend fun insert(item: ChecklistItemEntity): Long

    @Insert
    suspend fun insertAll(items: List<ChecklistItemEntity>): List<Long>

    @Upsert
    suspend fun upsertAll(items: List<ChecklistItemEntity>)

    @Query("DELETE FROM checklist_items WHERE noteId = :noteId AND id NOT IN (:keep)")
    suspend fun deleteOthers(noteId: Long, keep: List<Long>)

    @Query("DELETE FROM checklist_items WHERE noteId = :noteId")
    suspend fun deleteAllFor(noteId: Long)
}

@Dao
interface ReminderDao {
    @Query(
        """
        SELECT r.*, n.title AS noteTitle, n.kind AS noteKind FROM reminders r
        LEFT JOIN notes n ON n.id = r.noteId
        WHERE n.id IS NULL OR n.trashedAt IS NULL
        ORDER BY r.done ASC, r.dueAt ASC
        """
    )
    fun observeAll(): Flow<List<ReminderWithNote>>

    @Query("SELECT * FROM reminders WHERE noteId = :noteId AND done = 0 ORDER BY dueAt ASC")
    fun observeForNote(noteId: Long): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE done = 0")
    suspend fun pending(): List<ReminderEntity>

    @Query("SELECT * FROM reminders")
    suspend fun all(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE noteId IN (:noteIds)")
    suspend fun forNotes(noteIds: List<Long>): List<ReminderEntity>

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE done = 1")
    suspend fun clearCompleted()
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY id ASC")
    fun observe(noteId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE noteId IN (:noteIds)")
    suspend fun forNotes(noteIds: List<Long>): List<AttachmentEntity>

    @Query("SELECT * FROM attachments")
    suspend fun all(): List<AttachmentEntity>

    @Insert
    suspend fun insert(a: AttachmentEntity): Long

    @Delete
    suspend fun delete(a: AttachmentEntity)
}

@Dao
interface RevisionDao {
    @Query("SELECT * FROM revisions WHERE noteId = :noteId ORDER BY savedAt DESC")
    fun observe(noteId: Long): Flow<List<RevisionEntity>>

    @Insert
    suspend fun insert(r: RevisionEntity)

    @Query(
        """
        DELETE FROM revisions WHERE noteId = :noteId AND id NOT IN
        (SELECT id FROM revisions WHERE noteId = :noteId ORDER BY savedAt DESC LIMIT :keep)
        """
    )
    suspend fun trim(noteId: Long, keep: Int)
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity ORDER BY day ASC")
    fun observeAll(): Flow<List<ActivityEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(a: ActivityEntity): Long

    @Query("UPDATE activity SET edits = edits + 1 WHERE day = :day")
    suspend fun increment(day: Long)

    @Transaction
    suspend fun bump(day: Long) {
        if (insertIgnore(ActivityEntity(day, 1)) == -1L) increment(day)
    }
}
