package com.notecraft.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.notecraft.app.AppContainer
import com.notecraft.app.data.NoteRepository
import com.notecraft.app.data.db.NoteWithMeta
import com.notecraft.app.ui.LocalNavigator
import com.notecraft.app.ui.common.ConfirmDialog
import com.notecraft.app.ui.common.EmptyState
import com.notecraft.app.ui.common.LocalSnackbar
import com.notecraft.app.ui.common.NoteCard
import com.notecraft.app.ui.common.appViewModel
import com.notecraft.app.ui.common.rememberHaptics
import com.notecraft.app.ui.common.showUndo
import com.notecraft.app.ui.theme.NC
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class CollectionViewModel(c: AppContainer, val trash: Boolean) : ViewModel() {
    private val repo = c.repository
    val items = (if (trash) repo.trashed() else repo.archived())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val selection = MutableStateFlow<Set<Long>>(emptySet())

    fun toggle(id: Long) { selection.value = selection.value.let { if (id in it) it - id else it + id } }
    fun clear() { selection.value = emptySet() }
    fun restore(ids: List<Long>) = viewModelScope.launch { if (trash) repo.restore(ids) else repo.setArchived(ids, false) }
    fun undoRestore(ids: List<Long>) = viewModelScope.launch { if (trash) repo.trash(ids) else repo.setArchived(ids, true) }
    fun deleteForever(ids: List<Long>) = viewModelScope.launch { repo.deleteForever(ids) }
    fun moveToTrash(ids: List<Long>) = viewModelScope.launch { repo.trash(ids) }
    fun emptyTrash() = viewModelScope.launch { repo.emptyTrash() }
}

@Composable
fun SimpleCollectionScreen(trash: Boolean) {
    val vm = appViewModel(key = "collection-$trash") { CollectionViewModel(it, trash) }
    val items by vm.items.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<List<Long>?>(null) }
    val selecting = selection.isNotEmpty()
    BackHandler(selecting) { vm.clear() }

    fun restore(ids: List<Long>) {
        haptics.confirm(); vm.restore(ids); vm.clear()
        scope.launch { snackbar.showUndo(if (trash) "Restored" else "Unarchived") { vm.undoRestore(ids) } }
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalItemSpacing = 10.dp,
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Column(Modifier.statusBarsPadding().padding(top = 4.dp, bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = navigator::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                        Row(Modifier.weight(1f)) {}
                        if (trash && !items.isNullOrEmpty()) TextButton(onClick = { confirmEmpty = true }) { Text("Empty trash", color = NC.extra.danger) }
                    }
                    Text(if (trash) "Trash" else "Archive", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
                    Text(
                        if (trash) "Notes are deleted forever after ${NoteRepository.TRASH_DAYS} days. Tap one to restore it."
                        else "Out of sight, never lost. Tap a note to open it.",
                        style = MaterialTheme.typography.bodyMedium, color = NC.extra.muted, modifier = Modifier.padding(start = 6.dp, top = 4.dp)
                    )
                }
            }
            if (items?.isEmpty() == true) item(span = StaggeredGridItemSpan.FullLine) {
                if (trash) EmptyState(Icons.Outlined.DeleteOutline, "Trash is empty", "Deleted notes rest here for 30 days.")
                else EmptyState(Icons.Outlined.Archive, "Nothing archived", "Archive notes you're done with to keep your home calm.")
            }
            items(items.orEmpty(), key = { it.note.id }) { m ->
                NoteCard(
                    item = m, compact = false, selected = m.note.id in selection, selectionMode = selecting,
                    onClick = {
                        when {
                            selecting -> vm.toggle(m.note.id)
                            trash -> vm.toggle(m.note.id)
                            else -> navigator.open(m.note.id, m.note.isList)
                        }
                    },
                    onLongClick = { haptics.long(); vm.toggle(m.note.id) },
                    showTrashInfo = if (trash) daysLeft(m) else null,
                    modifier = Modifier.animateItem(),
                )
            }
        }
        AnimatedVisibility(selecting, enter = fadeIn(), exit = fadeOut()) {
            val ids = selection.toList()
            SelectionBar(
                count = selection.size, onClose = vm::clear,
                actions = buildList {
                    add(SelAction(if (trash) Icons.Outlined.Restore else Icons.Outlined.Unarchive, if (trash) "Restore" else "Unarchive") { restore(ids) })
                    if (trash) add(SelAction(Icons.Outlined.DeleteForever, "Delete forever") { confirmDelete = ids })
                    else add(SelAction(Icons.Outlined.DeleteOutline, "Delete") { haptics.confirm(); vm.moveToTrash(ids); vm.clear() })
                },
            )
        }
    }

    if (confirmEmpty) ConfirmDialog(
        "Empty trash?", "All notes in the trash will be permanently deleted. This can't be undone.", "Empty trash",
        onConfirm = { haptics.reject(); vm.emptyTrash() }, onDismiss = { confirmEmpty = false },
    )
    confirmDelete?.let { ids ->
        ConfirmDialog(
            "Delete forever?", "${if (ids.size == 1) "This note" else "${ids.size} notes"} will be permanently deleted.", "Delete",
            onConfirm = { haptics.reject(); vm.deleteForever(ids); vm.clear() }, onDismiss = { confirmDelete = null },
        )
    }
}

private fun daysLeft(m: NoteWithMeta): String {
    val trashed = m.note.trashedAt ?: return ""
    val left = NoteRepository.TRASH_DAYS - TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - trashed)
    return if (left <= 1) "Deletes today" else "$left days left"
}
