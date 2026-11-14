package com.notecraft.app.ui.common

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FormatColorReset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.notecraft.app.AppContainer
import com.notecraft.app.ui.theme.NC
import com.notecraft.app.ui.theme.NoteColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No container") }
val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("No snackbar host") }
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val c = LocalContainer.current
    return viewModel(key = key, factory = viewModelFactory { initializer { create(c) } })
}

/** Shared-bounds container transform between a card and its editor, when scopes are available. */
@Composable
fun Modifier.sharedNote(key: String?): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val anim = LocalNavAnimScope.current ?: return this
    if (key == null) return this
    return with(shared) {
        this@sharedNote.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = anim,
            enter = fadeIn(tween(220)),
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}

// ---- Haptics -----------------------------------------------------------------------------

class Haptics(private val view: View, private val enabled: Boolean) {
    fun tick() { if (enabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    fun tap() { if (enabled) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    fun long() { if (enabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
    fun confirm() {
        if (!enabled) return
        if (android.os.Build.VERSION.SDK_INT >= 30) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        else view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
    fun reject() {
        if (!enabled) return
        if (android.os.Build.VERSION.SDK_INT >= 30) view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        else view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val enabled = NC.extra.haptics
    return remember(view, enabled) { Haptics(view, enabled) }
}

// ---- Time formatting ---------------------------------------------------------------------

object Fmt {
    private val zone get() = ZoneId.systemDefault()
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val full = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
    private val weekday = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())
    private val weekdayShort = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

    fun relative(ms: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - ms
        val min = diff / 60_000
        val d = Instant.ofEpochMilli(ms).atZone(zone)
        val today = LocalDate.now(zone)
        return when {
            diff < 0 -> date(ms)
            min < 1 -> "Just now"
            min < 60 -> "${min}m ago"
            d.toLocalDate() == today -> time.format(d)
            d.toLocalDate() == today.minusDays(1) -> "Yesterday"
            d.toLocalDate().isAfter(today.minusDays(7)) -> weekday.format(d)
            d.year == today.year -> dayMonth.format(d)
            else -> dayMonthYear.format(d)
        }
    }

    fun date(ms: Long): String {
        val d = Instant.ofEpochMilli(ms).atZone(zone)
        return if (d.year == LocalDate.now(zone).year) dayMonth.format(d) else dayMonthYear.format(d)
    }

    fun time(ms: Long): String = time.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun full(ms: Long): String = full.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun fullWithTime(ms: Long): String = "${full(ms)} · ${time(ms)}"
    fun today(): String = full.format(LocalDate.now(zone))

    /** "Today 6:00 PM", "Tomorrow 9:00 AM", "Fri 6:00 PM", "12 Dec, 9:00 AM" */
    fun due(ms: Long): String {
        val d = Instant.ofEpochMilli(ms).atZone(zone)
        val today = LocalDate.now(zone)
        val t = time.format(d)
        return when (d.toLocalDate()) {
            today -> "Today, $t"
            today.plusDays(1) -> "Tomorrow, $t"
            today.minusDays(1) -> "Yesterday, $t"
            else -> if (d.toLocalDate().isAfter(today) && d.toLocalDate().isBefore(today.plusDays(7))) "${weekdayShort.format(d)}, $t"
            else "${date(ms)}, $t"
        }
    }

    fun greeting(): String = when (java.time.LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hello, night owl"
    }
}

// ---- Undo snackbars ------------------------------------------------------------------------

suspend fun SnackbarHostState.showUndo(message: String, onUndo: suspend () -> Unit) {
    currentSnackbarData?.dismiss()
    val r = showSnackbar(message, actionLabel = "Undo", withDismissAction = false, duration = SnackbarDuration.Short)
    if (r == SnackbarResult.ActionPerformed) onUndo()
}

// ---- Images --------------------------------------------------------------------------------

@Composable
fun AttachmentImage(fileName: String, modifier: Modifier = Modifier, reqSizePx: Int = 640, contentScale: ContentScale = ContentScale.Crop) {
    val store = LocalContainer.current.attachments
    var bmp by remember(fileName, reqSizePx) { mutableStateOf<ImageBitmap?>(store.cached(fileName, reqSizePx)) }
    LaunchedEffect(fileName, reqSizePx) { if (bmp == null) bmp = store.load(fileName, reqSizePx) }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        bmp?.let { Image(it, contentDescription = null, contentScale = contentScale, modifier = Modifier.matchParentSize()) }
    }
}

@Composable
fun Dp.toPx(): Int = with(LocalDensity.current) { this@toPx.roundToPx() }

// ---- Small building blocks -------------------------------------------------------------------

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = NC.extra.muted, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier = modifier,
        style = MaterialTheme.typography.labelMedium, color = NC.extra.muted,
    )
}

@Composable
fun ColorPickerRow(selected: Int, onSelect: (Int) -> Unit, contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp)) {
    val haptics = rememberHaptics()
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = contentPadding) {
        items(NoteColors.size) { i ->
            val c = NoteColors[i]
            val isSel = selected == i
            val fill = if (i == 0) NC.extra.card else NC.noteBg(i)
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(fill)
                    .border(if (isSel) 2.dp else 1.dp, if (isSel) MaterialTheme.colorScheme.primary else if (i == 0) NC.extra.cardBorder else c.swatch.copy(alpha = 0.55f), CircleShape)
                    .clickable { haptics.tick(); onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                when {
                    isSel -> Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    i == 0 -> Icon(Icons.Rounded.FormatColorReset, null, tint = NC.extra.muted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, color = NC.extra.muted) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm, color = if (destructive) NC.extra.danger else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
fun Pill(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier, tint: androidx.compose.ui.graphics.Color = NC.extra.muted) {
    Row(
        modifier.clip(CircleShape).background(tint.copy(alpha = 0.1f)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}
