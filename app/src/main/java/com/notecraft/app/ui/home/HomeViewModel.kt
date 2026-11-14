package com.notecraft.app.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notecraft.app.AppContainer
import com.notecraft.app.data.Layout
import com.notecraft.app.data.SortBy
import com.notecraft.app.data.TagCount
import com.notecraft.app.data.db.NoteKind
import com.notecraft.app.data.db.NoteWithMeta
import com.notecraft.app.domain.TextTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class HomeState(
    val loaded: Boolean = false,
    val pinned: List<NoteWithMeta> = emptyList(),
    val others: List<NoteWithMeta> = emptyList(),
    val tags: List<TagCount> = emptyList(),
    val selectedTag: String? = null,
    val layout: Int = Layout.GRID,
    val sort: Int = SortBy.MODIFIED,
    val total: Int = 0,
) {
    val isEmpty get() = pinned.isEmpty() && others.isEmpty()
}

class HomeViewModel(private val c: AppContainer, val kind: Int) : ViewModel() {
    private val repo = c.repository
    private val tag = MutableStateFlow<String?>(null)
    val selection = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<HomeState> = combine(
        repo.cards(kind),
        if (kind == NoteKind.NOTE) repo.tags() else flowOf(emptyList()),
        c.settings.settings,
        tag,
    ) { cards, tags, s, t ->
        val layout = if (kind == NoteKind.NOTE) s.notesLayout else s.listsLayout
        val filtered = if (t == null) cards else cards.filter { m ->
            TextTools.hashtags(m.note.body).any { it.equals(t, true) || it.startsWith("$t/", true) }
        }
        val sorted = when (s.sort) {
            SortBy.CREATED -> filtered.sortedByDescending { it.note.createdAt }
            SortBy.TITLE -> filtered.sortedBy { it.note.title.ifBlank { it.note.body }.lowercase().trim() }
            else -> filtered.sortedByDescending { it.note.updatedAt }
        }
        val (pinned, others) = sorted.partition { it.note.pinned }
        HomeState(true, pinned, others, tags, t?.takeIf { tg -> tags.any { it.tag.equals(tg, true) } }, layout, s.sort, cards.size)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun selectTag(t: String?) { tag.value = if (tag.value.equals(t, true)) null else t }

    fun toggleSelect(id: Long) { selection.value = selection.value.let { if (id in it) it - id else it + id } }
    fun clearSelection() { selection.value = emptySet() }
    fun selectAll() { selection.value = (state.value.pinned + state.value.others).map { it.note.id }.toSet() }

    fun setLayout(v: Int) = viewModelScope.launch { if (kind == NoteKind.NOTE) c.settings.setNotesLayout(v) else c.settings.setListsLayout(v) }
    fun setSort(v: Int) = viewModelScope.launch { c.settings.setSort(v) }

    fun pin(ids: List<Long>, pinned: Boolean) = viewModelScope.launch { repo.setPinned(ids, pinned) }
    fun color(ids: List<Long>, color: Int) = viewModelScope.launch { repo.setColor(ids, color) }
    fun archive(ids: List<Long>) = viewModelScope.launch { repo.setArchived(ids, true) }
    fun unarchive(ids: List<Long>) = viewModelScope.launch { repo.setArchived(ids, false) }
    fun trash(ids: List<Long>) = viewModelScope.launch { repo.trash(ids) }
    fun restore(ids: List<Long>) = viewModelScope.launch { repo.restore(ids) }
    fun duplicate(id: Long) = viewModelScope.launch { repo.duplicate(id) }
}
