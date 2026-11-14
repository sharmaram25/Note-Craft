package com.notecraft.app.data.db

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

object NoteKind {
    const val NOTE = 0
    const val LIST = 1
}

/**
 * A note or a checklist. Checklists keep their items in [ChecklistItemEntity]; their [body]
 * mirrors the item text so full-text search, previews and exports work uniformly.
 */
@Immutable
@Entity(
    tableName = "notes",
    indices = [Index("kind"), Index("updatedAt"), Index("trashedAt")]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: Int = NoteKind.NOTE,
    val title: String = "",
    val body: String = "",
    val color: Int = 0,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val locked: Boolean = false,
    val trashedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val isList get() = kind == NoteKind.LIST
}

@Fts4(contentEntity = NoteEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "notes_fts")
data class NoteFts(
    val title: String,
    val body: String,
)

@Immutable
@Entity(
    tableName = "checklist_items",
    foreignKeys = [ForeignKey(
        entity = NoteEntity::class,
        parentColumns = ["id"],
        childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("noteId")]
)
data class ChecklistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val text: String,
    val checked: Boolean = false,
    val position: Int = 0,
)

object Repeat {
    const val NONE = 0
    const val DAILY = 1
    const val WEEKDAYS = 2
    const val WEEKLY = 3
    const val MONTHLY = 4
    const val YEARLY = 5
}

@Immutable
@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(
        entity = NoteEntity::class,
        parentColumns = ["id"],
        childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("noteId"), Index("dueAt")]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dueAt: Long,
    val repeat: Int = Repeat.NONE,
    val noteId: Long? = null,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Immutable
@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(
        entity = NoteEntity::class,
        parentColumns = ["id"],
        childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("noteId")]
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val fileName: String,
    val width: Int,
    val height: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

@Immutable
@Entity(
    tableName = "revisions",
    foreignKeys = [ForeignKey(
        entity = NoteEntity::class,
        parentColumns = ["id"],
        childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("noteId")]
)
data class RevisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val title: String,
    val body: String,
    val savedAt: Long,
)

/** One row per calendar day (epoch day) that had writing activity. Powers streaks + heatmap. */
@Entity(tableName = "activity")
data class ActivityEntity(
    @PrimaryKey val day: Long,
    val edits: Int,
)

/** Card model for lists of notes: the note plus lightweight related data. */
@Immutable
data class NoteWithMeta(
    @Embedded val note: NoteEntity,
    @ColumnInfo(name = "itemCount") val itemCount: Int = 0,
    @ColumnInfo(name = "checkedCount") val checkedCount: Int = 0,
    @ColumnInfo(name = "nextReminder") val nextReminder: Long? = null,
    @ColumnInfo(name = "coverFile") val coverFile: String? = null,
    @ColumnInfo(name = "attachmentCount") val attachmentCount: Int = 0,
)

@Immutable
data class SearchHit(
    @Embedded val note: NoteEntity,
    @ColumnInfo(name = "snip") val snippet: String,
)

@Immutable
data class ReminderWithNote(
    @Embedded val reminder: ReminderEntity,
    @ColumnInfo(name = "noteTitle") val noteTitle: String?,
    @ColumnInfo(name = "noteKind") val noteKind: Int?,
)
