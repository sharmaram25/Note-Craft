package com.notecraft.app.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.withTransaction
import com.notecraft.app.data.db.AppDatabase
import com.notecraft.app.data.db.ChecklistItemEntity
import com.notecraft.app.data.db.NoteEntity
import com.notecraft.app.data.db.NoteKind
import com.notecraft.app.data.db.ReminderEntity
import com.notecraft.app.domain.TextTools
import java.io.File
import java.time.Instant

/**
 * Imports data from NoteCraft v2.x (React Native / expo-sqlite) when the native app is
 * installed over it. The legacy database is copied before reading and is renamed (never
 * deleted) once imported, so nothing can be lost.
 */
class LegacyMigrator(private val context: Context, private val db: AppDatabase) {

    data class Report(val notes: Int, val lists: Int, val reminders: Int)

    private fun legacyFile(): File? = listOf(
        File(context.filesDir, "SQLite/notecraft.db"),
        context.getDatabasePath("notecraft.db"),
    ).firstOrNull { it.exists() && it.length() > 0 }

    suspend fun migrateIfPresent(): Report? {
        val src = legacyFile() ?: return null
        val work = File(context.cacheDir, "legacy-import").apply { deleteRecursively(); mkdirs() }
        val copy = File(work, "legacy.db")
        src.copyTo(copy, overwrite = true)
        listOf("-wal", "-shm").forEach { s -> File(src.path + s).takeIf { it.exists() }?.copyTo(File(copy.path + s), overwrite = true) }

        val report = runCatching {
            val legacy = SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READWRITE)
            try { import(legacy) } finally { legacy.close() }
        }.onFailure { Log.e("NoteCraft", "Legacy import failed", it) }.getOrNull()

        work.deleteRecursively()
        if (report != null) {
            listOf("", "-wal", "-shm", "-journal").forEach { s ->
                File(src.path + s).takeIf { it.exists() }?.renameTo(File(src.path + s + ".migrated"))
            }
        }
        return report
    }

    private suspend fun import(legacy: SQLiteDatabase): Report {
        val notes = db.notes(); val items = db.checklist(); val reminders = db.reminders()
        var nNotes = 0; var nLists = 0; var nRem = 0
        db.withTransaction {
            if (legacy.hasTable("notes")) legacy.rawQuery("SELECT * FROM notes ORDER BY id", null).use { c ->
                while (c.moveToNext()) {
                    val title = c.str("title")
                    val raw = c.str("content")
                    var body = if (TextTools.looksLikeHtml(raw)) TextTools.htmlToMarkdown(raw) else raw.trim()
                    val tag = c.str("tag").trim().lowercase()
                    val color = when (tag) { "work" -> 6; "ideas" -> 3; "personal" -> 7; "research" -> 2; else -> 0 }
                    if (tag.isNotEmpty() && !body.contains("#$tag", ignoreCase = true)) body = (body + "\n\n#$tag").trim()
                    if (title.isBlank() && body.isBlank()) continue
                    notes.insert(NoteEntity(
                        kind = NoteKind.NOTE, title = title.trim(), body = body, color = color,
                        pinned = c.int("pinned") == 1,
                        createdAt = parseIso(c.str("created_at")), updatedAt = parseIso(c.str("updated_at")),
                    ))
                    nNotes++
                }
            }
            if (legacy.hasTable("lists")) legacy.rawQuery("SELECT * FROM lists ORDER BY id", null).use { c ->
                while (c.moveToNext()) {
                    val legacyId = c.long("id")
                    val created = parseIso(c.str("created_at"))
                    val newId = notes.insert(NoteEntity(kind = NoteKind.LIST, title = c.str("title").trim(), createdAt = created, updatedAt = created))
                    val list = mutableListOf<ChecklistItemEntity>()
                    if (legacy.hasTable("list_items")) legacy.rawQuery("SELECT * FROM list_items WHERE list_id = ? ORDER BY id", arrayOf(legacyId.toString())).use { ic ->
                        var pos = 0
                        while (ic.moveToNext()) list += ChecklistItemEntity(noteId = newId, text = ic.str("text"), checked = ic.int("completed") == 1, position = pos++)
                    }
                    if (list.isNotEmpty()) items.insertAll(list)
                    notes.updateBody(newId, TextTools.checklistBody(list), created)
                    nLists++
                }
            }
            if (legacy.hasTable("reminders")) legacy.rawQuery("SELECT * FROM reminders ORDER BY id", null).use { c ->
                val now = System.currentTimeMillis()
                while (c.moveToNext()) {
                    val due = parseIso(c.str("datetime"))
                    reminders.insert(ReminderEntity(
                        title = c.str("title").trim().ifEmpty { "Reminder" }, dueAt = due,
                        done = due < now, doneAt = if (due < now) due else null, createdAt = parseIso(c.str("created_at")),
                    ))
                    nRem++
                }
            }
        }
        return Report(nNotes, nLists, nRem)
    }

    private fun SQLiteDatabase.hasTable(name: String): Boolean =
        rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name)).use { it.moveToFirst() }

    private fun Cursor.str(col: String): String = getColumnIndex(col).takeIf { it >= 0 }?.let { if (isNull(it)) "" else getString(it) } ?: ""
    private fun Cursor.int(col: String): Int = getColumnIndex(col).takeIf { it >= 0 }?.let { if (isNull(it)) 0 else getInt(it) } ?: 0
    private fun Cursor.long(col: String): Long = getColumnIndex(col).takeIf { it >= 0 }?.let { getLong(it) } ?: 0L

    private fun parseIso(s: String): Long = runCatching { Instant.parse(s.trim()).toEpochMilli() }.getOrElse { System.currentTimeMillis() }
}
