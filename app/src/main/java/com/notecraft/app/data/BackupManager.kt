package com.notecraft.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.notecraft.app.BuildConfig
import com.notecraft.app.data.db.AppDatabase
import com.notecraft.app.data.db.AttachmentEntity
import com.notecraft.app.data.db.ChecklistItemEntity
import com.notecraft.app.data.db.NoteEntity
import com.notecraft.app.data.db.NoteKind
import com.notecraft.app.data.db.ReminderEntity
import com.notecraft.app.domain.TextTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Full-fidelity backups (.zip with JSON + images), Markdown export, and text/markdown import.
 * Restores always merge — they never overwrite or remove existing notes.
 */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val store: AttachmentStore,
    private val repo: NoteRepository,
) {
    data class Result(val notes: Int, val reminders: Int)

    suspend fun exportBackup(uri: Uri): Result = withContext(Dispatchers.IO) {
        val notes = db.notes().all()
        val items = db.checklist().all()
        val rems = db.reminders().all()
        val atts = db.attachments().all()
        val json = JSONObject().apply {
            put("format", "notecraft-backup")
            put("version", 1)
            put("app", BuildConfig.VERSION_NAME)
            put("exportedAt", System.currentTimeMillis())
            put("notes", JSONArray().apply { notes.forEach { put(it.toJson()) } })
            put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
            put("reminders", JSONArray().apply { rems.forEach { put(it.toJson()) } })
            put("attachments", JSONArray().apply { atts.forEach { put(it.toJson()) } })
        }
        context.contentResolver.openOutputStream(uri, "wt")!!.use { os ->
            ZipOutputStream(os.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("notecraft.json"))
                zip.write(json.toString().toByteArray())
                zip.closeEntry()
                atts.forEach { a ->
                    val f = store.file(a.fileName)
                    if (f.exists()) {
                        zip.putNextEntry(ZipEntry("attachments/${a.fileName}"))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }
        Result(notes.size, rems.size)
    }

    suspend fun importBackup(uri: Uri): Result = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var json: JSONObject? = null
        context.contentResolver.openInputStream(uri)!!.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    val name = e.name
                    when {
                        name == "notecraft.json" -> json = JSONObject(zip.readBytes().decodeToString())
                        name.startsWith("attachments/") && !e.isDirectory -> {
                            val safe = File(name).name
                            File(tmp, safe).outputStream().use { zip.copyTo(it) }
                        }
                    }
                    e = zip.nextEntry
                }
            }
        }
        val root = json ?: error("This file isn't a NoteCraft backup")
        require(root.optString("format") == "notecraft-backup") { "This file isn't a NoteCraft backup" }

        var nNotes = 0; var nRem = 0
        val idMap = HashMap<Long, Long>()
        db.withTransaction {
            root.getJSONArray("notes").forEachObj { o ->
                val newId = db.notes().insert(NoteEntity(
                    kind = o.optInt("kind"), title = o.optString("title"), body = o.optString("body"),
                    color = o.optInt("color"), pinned = o.optBoolean("pinned"), archived = o.optBoolean("archived"),
                    locked = o.optBoolean("locked"), trashedAt = if (o.isNull("trashedAt")) null else o.optLong("trashedAt"),
                    createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt"),
                ))
                idMap[o.getLong("id")] = newId; nNotes++
            }
            val items = mutableListOf<ChecklistItemEntity>()
            root.optJSONArray("items")?.forEachObj { o ->
                val nid = idMap[o.getLong("noteId")] ?: return@forEachObj
                items += ChecklistItemEntity(noteId = nid, text = o.optString("text"), checked = o.optBoolean("checked"), position = o.optInt("position"))
            }
            if (items.isNotEmpty()) db.checklist().insertAll(items)
            root.optJSONArray("reminders")?.forEachObj { o ->
                val nid = if (o.isNull("noteId")) null else idMap[o.getLong("noteId")]
                db.reminders().insert(ReminderEntity(
                    title = o.optString("title"), dueAt = o.optLong("dueAt"), repeat = o.optInt("repeat"), noteId = nid,
                    done = o.optBoolean("done"), doneAt = if (o.isNull("doneAt")) null else o.optLong("doneAt"), createdAt = o.optLong("createdAt"),
                ))
                nRem++
            }
        }
        root.optJSONArray("attachments")?.forEachObj { o ->
            val nid = idMap[o.getLong("noteId")] ?: return@forEachObj
            val f = File(tmp, File(o.optString("fileName")).name)
            if (!f.exists()) return@forEachObj
            val imported = store.importFile(f) ?: return@forEachObj
            db.attachments().insert(AttachmentEntity(noteId = nid, fileName = imported.fileName, width = o.optInt("width"), height = o.optInt("height")))
        }
        tmp.deleteRecursively()
        repo.rescheduleAll()
        Result(nNotes, nRem)
    }

    suspend fun exportMarkdown(uri: Uri): Int = withContext(Dispatchers.IO) {
        val notes = db.notes().all().filter { it.trashedAt == null }
        val used = HashSet<String>()
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())
        context.contentResolver.openOutputStream(uri, "wt")!!.use { os ->
            ZipOutputStream(os.buffered()).use { zip ->
                notes.forEach { n ->
                    val base = TextTools.safeFileName(n.title, "Untitled ${fmt.format(Instant.ofEpochMilli(n.createdAt))}")
                    var name = base; var i = 2
                    while (!used.add(name.lowercase())) name = "$base ($i)".also { i++ }
                    val folder = if (n.archived) "Archive/" else if (n.kind == NoteKind.LIST) "Lists/" else ""
                    val text = buildString {
                        if (n.title.isNotBlank()) append("# ").append(n.title.trim()).append("\n\n")
                        append(n.body)
                        append('\n')
                    }
                    zip.putNextEntry(ZipEntry("NoteCraft/$folder$name.md"))
                    zip.write(text.toByteArray())
                    zip.closeEntry()
                }
            }
        }
        notes.size
    }

    /** Imports .md / .txt files as individual notes. */
    suspend fun importTextFiles(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (uri in uris) {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull() ?: continue
            val fileName = displayName(uri)?.substringBeforeLast('.') ?: ""
            var body = text.replace("\r\n", "\n").trim()
            var title = fileName
            val firstLine = body.lineSequence().firstOrNull().orEmpty()
            if (firstLine.startsWith("# ")) { title = firstLine.removePrefix("# ").trim(); body = body.substringAfter('\n', "").trim() }
            if (title.isBlank() && body.isBlank()) continue
            repo.createNote(title = title, body = body)
            count++
        }
        count
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private inline fun JSONArray.forEachObj(block: (JSONObject) -> Unit) {
        for (i in 0 until length()) block(getJSONObject(i))
    }

    private fun NoteEntity.toJson() = JSONObject().apply {
        put("id", id); put("kind", kind); put("title", title); put("body", body); put("color", color)
        put("pinned", pinned); put("archived", archived); put("locked", locked)
        put("trashedAt", trashedAt ?: JSONObject.NULL); put("createdAt", createdAt); put("updatedAt", updatedAt)
    }

    private fun ChecklistItemEntity.toJson() = JSONObject().apply {
        put("id", id); put("noteId", noteId); put("text", text); put("checked", checked); put("position", position)
    }

    private fun ReminderEntity.toJson() = JSONObject().apply {
        put("id", id); put("title", title); put("dueAt", dueAt); put("repeat", repeat)
        put("noteId", noteId ?: JSONObject.NULL); put("done", done); put("doneAt", doneAt ?: JSONObject.NULL); put("createdAt", createdAt)
    }

    private fun AttachmentEntity.toJson() = JSONObject().apply {
        put("id", id); put("noteId", noteId); put("fileName", fileName); put("width", width); put("height", height)
    }
}
