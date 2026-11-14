package com.notecraft.app

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.notecraft.app.data.AttachmentStore
import com.notecraft.app.data.BackupManager
import com.notecraft.app.data.LegacyMigrator
import com.notecraft.app.data.NoteRepository
import com.notecraft.app.data.SettingsStore
import com.notecraft.app.data.WelcomeContent
import com.notecraft.app.data.db.AppDatabase
import com.notecraft.app.reminders.ReminderScheduler
import com.notecraft.app.widget.NotesWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

object AppIntents {
    const val ACTION_OPEN = "com.notecraft.app.action.OPEN"
    const val ACTION_NEW_NOTE = "com.notecraft.app.action.NEW_NOTE"
    const val ACTION_NEW_LIST = "com.notecraft.app.action.NEW_LIST"
    const val ACTION_NEW_REMINDER = "com.notecraft.app.action.NEW_REMINDER"
    const val ACTION_SEARCH = "com.notecraft.app.action.SEARCH"
    const val EXTRA_NOTE_ID = "note_id"
    const val EXTRA_TAB = "tab"
    const val TAB_NOTES = 0
    const val TAB_LISTS = 1
    const val TAB_REMINDERS = 2
}

class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = AppDatabase.build(app)
    val settings = SettingsStore(app)
    val attachments = AttachmentStore(app)
    val scheduler = ReminderScheduler(app)
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val repository = NoteRepository(db, attachments, scheduler) { changes.tryEmit(Unit) }
    val backup = BackupManager(app, db, attachments, repository)
    val lock = AppLock(this)

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        appScope.launch { changes.debounce(700).collect { NotesWidget.refresh(app) } }
        appScope.launch(Dispatchers.IO) {
            val s = settings.current()
            if (!s.legacyMigrated) {
                val report = LegacyMigrator(app, db).migrateIfPresent()
                settings.setLegacyMigrated()
                if (report != null) settings.setOnboarded()
            }
            if (!settings.current().onboarded) {
                if (db.notes().countAll() == 0) WelcomeContent.seed(repository)
                settings.setOnboarded()
            }
            _ready.value = true
            repository.purgeExpiredTrash()
            repository.rescheduleAll()
        }
    }
}

/** Tracks whether the app (or individual locked notes) currently require authentication. */
class AppLock(private val container: AppContainer) : DefaultLifecycleObserver {
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private var backgroundedAt = 0L
    private var initialized = false
    private val unlockedNotes = HashSet<Long>()

    fun install() = ProcessLifecycleOwner.get().lifecycle.addObserver(this)

    override fun onStart(owner: LifecycleOwner) {
        container.appScope.launch {
            val s = container.settings.current()
            if (!s.appLock) { _locked.value = false; initialized = true; return@launch }
            val elapsed = SystemClock.elapsedRealtime() - backgroundedAt
            if (!initialized || elapsed >= s.lockTimeoutSec * 1000L) _locked.value = true
            initialized = true
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = SystemClock.elapsedRealtime()
        unlockedNotes.clear()
    }

    fun unlock() { _locked.value = false }
    fun setDisabled() { _locked.value = false }

    fun isNoteUnlocked(id: Long) = id in unlockedNotes
    fun markNoteUnlocked(id: Long) { unlockedNotes += id }
}

class NoteCraftApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.lock.install()
        ReminderScheduler.createChannel(this)
    }
}
