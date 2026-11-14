package com.notecraft.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.notecraft.app.AppContainer
import com.notecraft.app.data.NoteRepository
import com.notecraft.app.data.db.ReminderWithNote
import com.notecraft.app.data.db.SearchHit
import com.notecraft.app.domain.TextTools
import com.notecraft.app.ui.LocalNavigator
import com.notecraft.app.ui.common.EmptyState
import com.notecraft.app.ui.common.Fmt
import com.notecraft.app.ui.common.SectionLabel
import com.notecraft.app.ui.common.appViewModel
import com.notecraft.app.ui.common.sharedNote
import com.notecraft.app.ui.theme.NC
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class SearchResults(val query: String = "", val notes: List<SearchHit> = emptyList(), val reminders: List<ReminderWithNote> = emptyList(), val done: Boolean = false)

class SearchViewModel(private val c: AppContainer, initialTag: String?) : ViewModel() {
    val query = MutableStateFlow(initialTag?.let { "#$it" } ?: "")
    val recent = c.settings.settings.mapLatestRecent()
    val tags = c.repository.tags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val results = combine(query.debounce(120).distinctUntilChanged(), c.repository.observeReminders()) { q, rems -> q to rems }
        .mapLatest { (q, rems) ->
            if (q.isBlank()) SearchResults()
            else {
                val notes = c.repository.search(q).filter { !it.note.locked || it.note.title.contains(q.trim(), true) }
                val ql = q.trim().removePrefix("#").lowercase()
                val r = rems.filter { it.reminder.title.lowercase().contains(ql) }.take(10)
                SearchResults(q, notes, r, true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun commit() = viewModelScope.launch { c.settings.addRecentSearch(query.value) }
    fun clearRecent() = viewModelScope.launch { c.settings.clearRecentSearches() }

    private fun kotlinx.coroutines.flow.Flow<com.notecraft.app.data.Settings>.mapLatestRecent() =
        mapLatest { it.recentSearches }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun SearchScreen(initialTag: String?) {
    val vm = appViewModel(key = "search-$initialTag") { SearchViewModel(it, initialTag) }
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { if (initialTag == null) focus.requestFocus() }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().sharedNote("search")
                .clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, NC.extra.cardBorder, CircleShape).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = navigator::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            BasicTextField(
                value = query, onValueChange = { vm.query.value = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.commit(); focusManager.clearFocus() }),
                modifier = Modifier.weight(1f).padding(vertical = 14.dp).focusRequester(focus),
                decorationBox = { inner -> Box { if (query.isEmpty()) Text("Search everything", color = NC.extra.muted, style = MaterialTheme.typography.bodyLarge); inner() } }
            )
            if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = ""; focus.requestFocus() }) { Icon(Icons.Rounded.Close, "Clear", tint = NC.extra.muted) }
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 40.dp), modifier = Modifier.fillMaxSize()) {
            if (query.isBlank()) {
                if (recent.isNotEmpty()) {
                    item {
                        Row(Modifier.padding(start = 24.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel("Recent", Modifier.weight(1f))
                            TextButton(onClick = vm::clearRecent) { Text("Clear", color = NC.extra.muted) }
                        }
                    }
                    items(recent) { r ->
                        Row(Modifier.fillMaxWidth().clickable { vm.query.value = r }.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.History, null, tint = NC.extra.faint, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(16.dp))
                            Text(r, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (tags.isNotEmpty()) {
                    item { SectionLabel("Tags", Modifier.padding(start = 24.dp, top = 20.dp, bottom = 10.dp)) }
                    item {
                        FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            tags.forEach { t ->
                                Row(
                                    Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                        .clickable { vm.query.value = "#${t.tag}" }.padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text("#${t.tag}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                    Spacer(Modifier.width(6.dp))
                                    Text("${t.count}", color = NC.extra.muted, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
                if (recent.isEmpty() && tags.isEmpty()) item {
                    Text(
                        "Search titles, text, checklist items and #tags. Results appear as you type.",
                        color = NC.extra.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                if (results.done && results.notes.isEmpty() && results.reminders.isEmpty()) item {
                    EmptyState(Icons.Rounded.SearchOff, "No matches", "Try fewer words, or check the spelling.")
                }
                if (results.notes.isNotEmpty()) {
                    item { SectionLabel("Notes & lists · ${results.notes.size}", Modifier.padding(start = 24.dp, top = 12.dp, bottom = 6.dp)) }
                    items(results.notes, key = { it.note.id }) { hit ->
                        HitRow(hit) { vm.commit(); navigator.open(hit.note.id, hit.note.isList) }
                    }
                }
                if (results.reminders.isNotEmpty()) {
                    item { SectionLabel("Reminders", Modifier.padding(start = 24.dp, top = 18.dp, bottom = 6.dp)) }
                    items(results.reminders, key = { "r-${it.reminder.id}" }) { r ->
                        Row(
                            Modifier.fillMaxWidth().clickable { navigator.tab(2) }.padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Alarm, null, tint = NC.extra.muted, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(r.reminder.title, style = MaterialTheme.typography.bodyLarge)
                                Text(Fmt.due(r.reminder.dueAt) + if (r.reminder.done) " · done" else "", style = MaterialTheme.typography.labelMedium, color = NC.extra.muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HitRow(hit: SearchHit, onClick: () -> Unit) {
    val n = hit.note
    val accent = MaterialTheme.colorScheme.primary
    val snippet = remember(hit.snippet) { highlight(TextTools.inlineStrip(hit.snippet.replace('\n', ' ')), accent) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Icon(
            when { n.locked -> Icons.Rounded.Lock; n.isList -> Icons.Outlined.Checklist; else -> Icons.AutoMirrored.Outlined.Notes },
            null, tint = NC.extra.muted, modifier = Modifier.padding(top = 2.dp).size(20.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (n.archived) { Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.Archive, "Archived", tint = NC.extra.faint, modifier = Modifier.size(14.dp)) }
                Spacer(Modifier.width(8.dp))
                Text(Fmt.relative(n.updatedAt), style = MaterialTheme.typography.labelSmall, color = NC.extra.faint)
            }
            if (!n.locked && snippet.isNotEmpty()) Text(snippet, style = MaterialTheme.typography.bodyMedium, color = NC.extra.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun highlight(s: String, accent: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        val start = s.indexOf(NoteRepository.HL_START, i)
        if (start < 0) { append(s.substring(i)); break }
        append(s.substring(i, start))
        val end = s.indexOf(NoteRepository.HL_END, start).let { if (it < 0) s.length else it }
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold, background = accent.copy(alpha = 0.1f))) { append(s.substring(start + 1, end)) }
        i = end + 1
    }
}
