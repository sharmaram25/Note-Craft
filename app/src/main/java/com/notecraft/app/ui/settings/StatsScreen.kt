package com.notecraft.app.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.notecraft.app.AppContainer
import com.notecraft.app.data.TagCount
import com.notecraft.app.data.db.NoteKind
import com.notecraft.app.domain.TextTools
import com.notecraft.app.ui.LocalNavigator
import com.notecraft.app.ui.common.appViewModel
import com.notecraft.app.ui.theme.NC
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

@Immutable
data class Stats(
    val notes: Int = 0,
    val lists: Int = 0,
    val words: Int = 0,
    val streak: Int = 0,
    val longestStreak: Int = 0,
    val activeDays: Int = 0,
    val itemsDone: Int = 0,
    val itemsTotal: Int = 0,
    val heat: Map<Long, Int> = emptyMap(),
    val tags: List<TagCount> = emptyList(),
)

class StatsViewModel(c: AppContainer) : ViewModel() {
    private val repo = c.repository
    val stats = combine(repo.cards(NoteKind.NOTE), repo.cards(NoteKind.LIST), repo.observeActivity(), repo.tags()) { notes, lists, activity, tags ->
        val words = notes.sumOf { TextTools.wordCount(it.note.title + " " + it.note.body) }
        val heat = activity.associate { it.day to it.edits }
        val today = LocalDate.now().toEpochDay()
        var streak = 0
        var d = if (heat.containsKey(today)) today else today - 1
        while (heat.containsKey(d)) { streak++; d-- }
        var longest = 0; var run = 0; var prev = Long.MIN_VALUE
        activity.map { it.day }.sorted().forEach { day -> run = if (day == prev + 1) run + 1 else 1; longest = maxOf(longest, run); prev = day }
        Stats(
            notes = notes.size, lists = lists.size, words = words, streak = streak, longestStreak = longest, activeDays = activity.size,
            itemsDone = lists.sumOf { it.checkedCount }, itemsTotal = lists.sumOf { it.itemCount }, heat = heat, tags = tags.take(6),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun StatsScreen() {
    val vm = appViewModel(key = "stats") { StatsViewModel(it) }
    val stats by vm.stats.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val s = stats ?: Stats()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()) {
        IconButton(onClick = navigator::back, modifier = Modifier.padding(4.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Text("Insights", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 20.dp))
        Text(
            when {
                s.streak >= 7 -> "A ${s.streak}-day streak. Your future self says thanks."
                s.streak > 1 -> "${s.streak} days in a row. Keep the thread going."
                s.streak == 1 -> "You wrote today. That's how habits begin."
                else -> "A few words a day adds up quickly."
            },
            style = MaterialTheme.typography.bodyMedium, color = NC.extra.muted, modifier = Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp)
        )
        Spacer(Modifier.height(20.dp))
        Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("${s.streak}", "day streak", Modifier.weight(1f), highlight = true)
            Tile(formatCount(s.words), "words written", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("${s.notes}", "notes", Modifier.weight(1f))
            Tile("${s.lists}", "lists", Modifier.weight(1f))
            Tile("${s.longestStreak}", "best streak", Modifier.weight(1f))
        }

        Card("Activity", "Last 20 weeks · ${s.activeDays} active days") { Heatmap(s.heat) }

        if (s.itemsTotal > 0) Card("Checklists", "${s.itemsDone} of ${s.itemsTotal} items completed") {
            Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))) {
                Box(Modifier.fillMaxWidth(s.itemsDone / s.itemsTotal.toFloat()).fillMaxHeight().clip(CircleShape).background(NC.extra.success))
            }
        }

        if (s.tags.isNotEmpty()) Card("Top tags", null) {
            val max = s.tags.maxOf { it.count }.toFloat()
            s.tags.forEach { t ->
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("#${t.tag}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(110.dp), maxLines = 1)
                    Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))) {
                        Box(Modifier.fillMaxWidth(t.count / max).fillMaxHeight().clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)))
                    }
                    Text("${t.count}", style = MaterialTheme.typography.labelMedium, color = NC.extra.muted, modifier = Modifier.padding(start = 10.dp).width(24.dp))
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

private fun formatCount(n: Int) = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f)
    n >= 10_000 -> "%.1fk".format(n / 1000f)
    else -> "%,d".format(n)
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier, highlight: Boolean = false) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp))
            .background(if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else NC.extra.cardBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelMedium, color = NC.extra.muted)
    }
}

@Composable
private fun Card(title: String, subtitle: String?, content: @Composable () -> Unit) {
    Column(
        Modifier.padding(horizontal = 14.dp).padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest).border(1.dp, NC.extra.cardBorder, RoundedCornerShape(22.dp)).padding(18.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = NC.extra.muted)
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun Heatmap(heat: Map<Long, Int>) {
    val weeks = 20
    val today = LocalDate.now()
    // Align so the last column is the current week (Mon..Sun rows).
    val endOfWeek = today.plusDays((7 - today.dayOfWeek.value).toLong())
    val start = endOfWeek.minusDays(weeks * 7L - 1)
    val accent = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val max = (heat.values.maxOrNull() ?: 1).coerceAtLeast(4)
    Column {
        Row(Modifier.padding(start = 22.dp)) {
            var lastMonth = -1
            for (w in 0 until weeks) {
                val d = start.plusWeeks(w.toLong())
                val label = if (d.monthValue != lastMonth) { lastMonth = d.monthValue; d.month.getDisplayName(JTextStyle.SHORT, Locale.getDefault()) } else ""
                Box(Modifier.weight(1f)) { if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelSmall, color = NC.extra.muted, maxLines = 1, softWrap = false) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row {
            Column(Modifier.width(22.dp).height(105.dp), verticalArrangement = Arrangement.SpaceBetween) {
                listOf("M", "", "W", "", "F", "", "S").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = NC.extra.faint) }
            }
            Canvas(Modifier.weight(1f).height(105.dp)) {
                val gap = 3.dp.toPx()
                val cell = minOf((size.width - gap * (weeks - 1)) / weeks, (size.height - gap * 6) / 7)
                for (w in 0 until weeks) for (dow in 0 until 7) {
                    val date = start.plusDays(w * 7L + dow)
                    if (date.isAfter(today)) continue
                    val v = heat[date.toEpochDay()] ?: 0
                    val color = if (v == 0) empty else accent.copy(alpha = 0.25f + 0.75f * (v.coerceAtMost(max) / max.toFloat()))
                    drawRoundRect(color, Offset(w * (cell + gap), dow * (cell + gap)), Size(cell, cell), CornerRadius(cell * 0.28f))
                }
            }
        }
        Row(Modifier.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text("Less", style = MaterialTheme.typography.labelSmall, color = NC.extra.faint)
            listOf(0f, 0.35f, 0.6f, 0.85f, 1f).forEach { a ->
                Box(Modifier.padding(horizontal = 2.dp).size(10.dp).clip(RoundedCornerShape(3.dp)).background(if (a == 0f) empty else accent.copy(alpha = a)))
            }
            Text("More", style = MaterialTheme.typography.labelSmall, color = NC.extra.faint)
        }
    }
}
