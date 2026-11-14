package com.notecraft.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        NoteEntity::class,
        NoteFts::class,
        ChecklistItemEntity::class,
        ReminderEntity::class,
        AttachmentEntity::class,
        RevisionEntity::class,
        ActivityEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao
    abstract fun checklist(): ChecklistDao
    abstract fun reminders(): ReminderDao
    abstract fun attachments(): AttachmentDao
    abstract fun revisions(): RevisionDao
    abstract fun activity(): ActivityDao

    companion object {
        // Distinct file name so it never collides with the legacy Expo database.
        private const val NAME = "notecraft-native.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
