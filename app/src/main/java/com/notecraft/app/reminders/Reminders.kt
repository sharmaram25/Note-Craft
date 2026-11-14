package com.notecraft.app.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.notecraft.app.AppIntents
import com.notecraft.app.MainActivity
import com.notecraft.app.NoteCraftApp
import com.notecraft.app.R
import com.notecraft.app.data.ReminderScheduling
import com.notecraft.app.data.db.ReminderEntity
import com.notecraft.app.data.db.Repeat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

const val REMINDER_CHANNEL = "reminders"

class ReminderScheduler(private val context: Context) : ReminderScheduling {

    private val am get() = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    override fun schedule(reminder: ReminderEntity) {
        cancel(reminder.id)
        if (reminder.done || reminder.dueAt <= System.currentTimeMillis()) return
        setAlarm(reminder.dueAt, firePending(reminder.id, snoozeOnly = false))
    }

    /** One-off re-alert used when snoozing a repeating reminder, leaving the series untouched. */
    fun scheduleSnoozeOnly(id: Long, at: Long) = setAlarm(at, firePending(id, snoozeOnly = true))

    private fun setAlarm(at: Long, pi: PendingIntent) {
        runCatching {
            if (canScheduleExact()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    override fun cancel(reminderId: Long) {
        am.cancel(firePending(reminderId, snoozeOnly = false))
        am.cancel(firePending(reminderId, snoozeOnly = true))
    }

    private fun firePending(id: Long, snoozeOnly: Boolean): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(if (snoozeOnly) ReminderReceiver.ACTION_FIRE_SNOOZE else ReminderReceiver.ACTION_FIRE)
            .putExtra(ReminderReceiver.EXTRA_ID, id)
        return PendingIntent.getBroadcast(context, requestCode(id, if (snoozeOnly) 1 else 0), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        fun requestCode(id: Long, slot: Int) = (id * 8 + slot).toInt()

        fun createChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(REMINDER_CHANNEL, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_reminders_desc)
                enableVibration(true)
            }
            nm.createNotificationChannel(ch)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1)
        if (id < 0) return
        val app = context.applicationContext as NoteCraftApp
        val repo = app.container.repository
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> {
                        val r = repo.getReminder(id)
                        if (r != null && !r.done) {
                            post(context, r)
                            repo.onReminderFired(id)
                        }
                    }
                    ACTION_FIRE_SNOOZE -> repo.getReminder(id)?.let { post(context, it) }
                    ACTION_DONE -> {
                        NotificationManagerCompat.from(context).cancel(notificationId(id))
                        val r = repo.getReminder(id)
                        // Repeating reminders already rolled forward when they fired.
                        if (r != null && r.repeat == Repeat.NONE) repo.completeReminder(id)
                    }
                    ACTION_SNOOZE -> {
                        NotificationManagerCompat.from(context).cancel(notificationId(id))
                        val minutes = intent.getLongExtra(EXTRA_MINUTES, 10)
                        val r = repo.getReminder(id)
                        if (r != null) {
                            if (r.repeat == Repeat.NONE) repo.snoozeReminder(id, minutes)
                            else app.container.scheduler.scheduleSnoozeOnly(id, System.currentTimeMillis() + minutes * 60_000)
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun post(context: Context, r: ReminderEntity) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val app = context.applicationContext as NoteCraftApp
        val note = r.noteId?.let { app.container.repository.get(it) }

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = AppIntents.ACTION_OPEN
            if (note != null) putExtra(AppIntents.EXTRA_NOTE_ID, note.id) else putExtra(AppIntents.EXTRA_TAB, AppIntents.TAB_REMINDERS)
        }
        val contentPi = PendingIntent.getActivity(context, ReminderScheduler.requestCode(r.id, 2), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        fun action(act: String, slot: Int, minutes: Long = 0) = PendingIntent.getBroadcast(
            context, ReminderScheduler.requestCode(r.id, slot),
            Intent(context, ReminderReceiver::class.java).setAction(act).putExtra(EXTRA_ID, r.id).putExtra(EXTRA_MINUTES, minutes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = when {
            note != null && note.title.isNotBlank() && !note.title.equals(r.title, true) -> note.title
            else -> DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(r.dueAt))
        }

        val n = NotificationCompat.Builder(context, REMINDER_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notecraft)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setContentTitle(r.title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .addAction(0, context.getString(R.string.action_done), action(ACTION_DONE, 3))
            .addAction(0, context.getString(R.string.action_snooze_10), action(ACTION_SNOOZE, 4, 10))
            .addAction(0, context.getString(R.string.action_snooze_60), action(ACTION_SNOOZE, 5, 60))
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(r.id), n)
    }

    companion object {
        const val ACTION_FIRE = "com.notecraft.app.reminder.FIRE"
        const val ACTION_FIRE_SNOOZE = "com.notecraft.app.reminder.FIRE_SNOOZE"
        const val ACTION_DONE = "com.notecraft.app.reminder.DONE"
        const val ACTION_SNOOZE = "com.notecraft.app.reminder.SNOOZE"
        const val EXTRA_ID = "id"
        const val EXTRA_MINUTES = "minutes"
        fun notificationId(id: Long) = (id % Int.MAX_VALUE).toInt()
    }
}

/** Re-arms alarms after reboot, app update, or when exact-alarm permission is granted. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as NoteCraftApp
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { app.container.repository.rescheduleAll() } finally { pending.finish() }
        }
    }
}
