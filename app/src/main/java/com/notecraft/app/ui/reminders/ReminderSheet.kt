package com.notecraft.app.ui.reminders

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.notecraft.app.data.db.ReminderEntity
import com.notecraft.app.data.db.Repeat
import com.notecraft.app.domain.NaturalDate
import com.notecraft.app.ui.common.Fmt
import com.notecraft.app.ui.common.LocalContainer
import com.notecraft.app.ui.common.rememberHaptics
import com.notecraft.app.ui.theme.NC
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

val RepeatLabels = listOf(Repeat.NONE to "Never", Repeat.DAILY to "Daily", Repeat.WEEKDAYS to "Weekdays", Repeat.WEEKLY to "Weekly", Repeat.MONTHLY to "Monthly", Repeat.YEARLY to "Yearly")
fun repeatLabel(r: Int) = RepeatLabels.firstOrNull { it.first == r }?.second ?: "Never"

/**
 * Create or edit a reminder. Type naturally ("Call mom tomorrow 6pm") or use quick picks.
 */
@Composable
fun ReminderSheet(
    initial: ReminderEntity?,
    defaultTitle: String = "",
    titleRequired: Boolean = true,
    onSave: (title: String, dueAt: Long, repeat: Int) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val scheduler = LocalContainer.current.scheduler
    var text by remember { mutableStateOf(initial?.title ?: defaultTitle) }
    var due by remember { mutableLongStateOf(initial?.dueAt ?: NaturalDate.defaultDue()) }
    var repeat by remember { mutableIntStateOf(initial?.repeat ?: Repeat.NONE) }
    var manuallyPicked by remember { mutableStateOf(initial != null) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    val parsed = remember(text) { NaturalDate.parse(text) }
    val detected = parsed.dueAt != null || parsed.repeat != Repeat.NONE
    LaunchedEffect(parsed) {
        if (!manuallyPicked) {
            parsed.dueAt?.let { due = it }
            if (parsed.repeat != Repeat.NONE) repeat = parsed.repeat
        }
    }
    LaunchedEffect(Unit) { if (initial == null && defaultTitle.isEmpty()) focus.requestFocus() }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val finalTitle = (if (detected && !manuallyPicked && parsed.title.isNotBlank()) parsed.title else text.trim()).ifBlank { defaultTitle }
    val canSave = !titleRequired || finalTitle.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (initial == null) "New reminder" else "Edit reminder", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (onDelete != null) IconButton(onClick = { haptics.confirm(); onDelete(); onDismiss() }) {
                    Icon(Icons.Outlined.DeleteOutline, "Delete reminder", tint = NC.extra.danger)
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                placeholder = { Text("e.g. Call mom tomorrow at 6pm") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).focusRequester(focus),
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = NC.extra.cardBorder,
                    focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            AnimatedVisibility(detected && !manuallyPicked) {
                Row(Modifier.padding(start = 26.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        buildString {
                            append("Understood: ")
                            parsed.dueAt?.let { append(Fmt.due(it)) }
                            if (parsed.repeat != Repeat.NONE) append(" · repeats ${repeatLabel(parsed.repeat).lowercase()}")
                        },
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            // Big, legible chosen time.
            Row(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .clickable { showDate = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(Fmt.due(due), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (due < System.currentTimeMillis()) "This time has passed — pick another" else "Tap to change date & time",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (due < System.currentTimeMillis()) NC.extra.danger else NC.extra.muted
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val quick = remember { quickPicks() }
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(quick) { (label, at) ->
                    Choice(label, selected = false) { haptics.tick(); due = at; manuallyPicked = true }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Repeat, null, tint = NC.extra.muted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Repeat", style = MaterialTheme.typography.labelLarge, color = NC.extra.muted)
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RepeatLabels.forEach { (v, l) -> Choice(l, repeat == v) { haptics.tick(); repeat = v; manuallyPicked = true } }
            }

            if (!scheduler.canScheduleExact()) {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(MaterialTheme.shapes.medium)
                        .background(NC.extra.danger.copy(alpha = 0.08f))
                        .clickable {
                            if (Build.VERSION.SDK_INT >= 31) runCatching {
                                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                            }
                        }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.WarningAmber, null, tint = NC.extra.danger, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Allow precise alarms so reminders arrive exactly on time. Tap to allow.", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(22.dp))
            Button(
                onClick = {
                    haptics.confirm()
                    ensureNotificationPermission()
                    onSave(finalTitle, due, repeat)
                    onDismiss()
                },
                enabled = canSave,
                shape = CircleShape,
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            ) { Text(if (initial == null) "Set reminder" else "Save changes", style = MaterialTheme.typography.labelLarge) }
        }
    }

    if (showDate) {
        val zone = ZoneId.systemDefault()
        val localDate = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        val t = Instant.ofEpochMilli(due).atZone(zone).toLocalTime()
                        due = LocalDateTime.of(d, t).atZone(zone).toInstant().toEpochMilli()
                        manuallyPicked = true
                    }
                    showDate = false; showTime = true
                }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state, showModeToggle = false) }
    }
    if (showTime) {
        val zone = ZoneId.systemDefault()
        val lt = Instant.ofEpochMilli(due).atZone(zone).toLocalTime()
        val state = rememberTimePickerState(initialHour = lt.hour, initialMinute = lt.minute, is24Hour = android.text.format.DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { showTime = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Time") },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    val d = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
                    due = LocalDateTime.of(d, LocalTime.of(state.hour, state.minute)).atZone(zone).toInstant().toEpochMilli()
                    manuallyPicked = true
                    showTime = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Text(
        label, color = fg, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clip(CircleShape).background(bg)
            .border(1.dp, if (selected) Color.Transparent else NC.extra.cardBorder, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp)
    )
}

private fun quickPicks(): List<Pair<String, Long>> {
    val zone = ZoneId.systemDefault()
    val now = LocalDateTime.now()
    fun ms(dt: LocalDateTime) = dt.atZone(zone).toInstant().toEpochMilli()
    val today = LocalDate.now()
    val evening = LocalDateTime.of(today, LocalTime.of(18, 0)).let { if (it.isBefore(now.plusMinutes(15))) it.plusDays(1) else it }
    return listOf(
        "In 1 hour" to ms(now.plusHours(1).withSecond(0).withNano(0)),
        (if (evening.toLocalDate() == today) "This evening" else "Tomorrow evening") to ms(evening),
        "Tomorrow morning" to ms(LocalDateTime.of(today.plusDays(1), LocalTime.of(9, 0))),
        "This weekend" to ms(LocalDateTime.of(today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)), LocalTime.of(10, 0))),
        "Next week" to ms(LocalDateTime.of(today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)), LocalTime.of(9, 0))),
    )
}
