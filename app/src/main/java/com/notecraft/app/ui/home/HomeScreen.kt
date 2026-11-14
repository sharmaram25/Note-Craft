package com.notecraft.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notecraft.app.data.Layout
import com.notecraft.app.data.SortBy
import com.notecraft.app.data.db.NoteKind
import com.notecraft.app.data.db.NoteWithMeta
import com.notecraft.app.ui.LocalNavigator
import com.notecraft.app.ui.common.ColorPickerRow
import com.notecraft.app.ui.common.EmptyState
import com.notecraft.app.ui.common.Fmt
import com.notecraft.app.ui.common.LocalSnackbar
import com.notecraft.app.ui.common.NoteCard
import com.notecraft.app.ui.common.SectionLabel
import com.notecraft.app.ui.common.appViewModel
import com.notecraft.app.ui.common.rememberHaptics
import com.notecraft.app.ui.common.sharedNote
import com.notecraft.app.ui.common.showUndo
import com.notecraft.app.ui.theme.NC
import kotlinx.coroutines.launch

@Composable
fun NotesHomeScreen(kind: Int, onSelectionMode: (Boolean) -> Unit) {
    val vm = appViewModel(key = "home-$kind") { HomeViewModel(it, kind) }
    val state by vm.state.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val selecting = selection.isNotEmpty()
    var colorSheet by remember { mutableStateOf(false) }
    val isNotes = kind == NoteKind.NOTE

    LaunchedEffect(selecting) { onSelectionMode(selecting) }
    BackHandler(selecting) { vm.clearSelection() }

    val all = remember(state) { state.pinned + state.others }
    fun open(m: NoteWithMeta) = navigator.open(m.note.id, m.note.isList, "note-${m.note.id}")
    fun trashWithUndo(ids: List<Long>) {
        haptics.confirm()
        vm.trash(ids)
        vm.clearSelection()
        scope.launch { snackbar.showUndo(if (ids.size == 1) "Moved to trash" else "${ids.size} moved to trash") { vm.restore(ids) } }
    }
    fun archiveWithUndo(ids: List<Long>) {
        haptics.confirm()
        vm.archive(ids)
        vm.clearSelection()
        scope.launch { snackbar.showUndo(if (ids.size == 1) "Archived" else "${ids.size} archived") { vm.unarchive(ids) } }
    }

    val compact = state.layout == Layout.LIST
    Box(Modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(if (compact) 1 else 2),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 132.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalItemSpacing = 10.dp,
            modifier = Modifier.fillMaxSize(),
        ) {
            fullLine("status") { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
            fullLine("header") {
                Header(
                    title = if (isNotes) "Notes" else "Lists",
                    subtitle = if (isNotes) Fmt.greeting() else "${state.total} ${if (state.total == 1) "list" else "lists"}",
                    layout = state.layout, sort = state.sort,
                    onLayout = { vm.setLayout(if (compact) Layout.GRID else Layout.LIST) },
                    onSort = vm::setSort,
                )
            }
            fullLine("search") { SearchPill(isNotes) { navigator.search() } }
            if (isNotes && state.tags.isNotEmpty()) fullLine("tags") {
                TagRow(state.tags.map { it.tag }, state.selectedTag, vm::selectTag)
            }
            if (state.loaded && state.isEmpty) fullLine("empty") {
                if (state.selectedTag != null) EmptyState(Icons.Rounded.Search, "Nothing tagged #${state.selectedTag}", "Tap the tag again to see everything.")
                else if (isNotes) EmptyState(Icons.Outlined.StickyNote2, "A blank page", "Tap + to capture your first thought. It saves as you type.")
                else EmptyState(Icons.Outlined.Checklist, "No lists yet", "Groceries, packing, today's priorities — tap + to start one.")
            }
            if (state.pinned.isNotEmpty()) {
                if (state.others.isNotEmpty()) fullLine("lbl-pinned") { SectionLabel("Pinned", Modifier.padding(start = 6.dp, top = 6.dp)) }
                cards(state.pinned, compact, selection, selecting, ::open, vm, haptics, ::trashWithUndo)
            }
            if (state.others.isNotEmpty()) {
                if (state.pinned.isNotEmpty()) fullLine("lbl-others") { SectionLabel("Others", Modifier.padding(start = 6.dp, top = 10.dp)) }
                cards(state.others, compact, selection, selecting, ::open, vm, haptics, ::trashWithUndo)
            }
        }

        AnimatedVisibility(
            selecting,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            val ids = selection.toList()
            val allPinned = all.filter { it.note.id in selection }.all { it.note.pinned }
            SelectionBar(
                count = selection.size,
                onClose = vm::clearSelection,
                actions = listOf(
                    SelAction(if (allPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin, if (allPinned) "Unpin" else "Pin") {
                        haptics.tick(); vm.pin(ids, !allPinned); vm.clearSelection()
                    },
                    SelAction(Icons.Outlined.Palette, "Color") { colorSheet = true },
                    SelAction(Icons.Outlined.Archive, "Archive") { archiveWithUndo(ids) },
                    SelAction(Icons.Outlined.Delete, "Delete") { trashWithUndo(ids) },
                ),
                more = buildList {
                    add("Select all" to { vm.selectAll() })
                    if (ids.size == 1) add("Make a copy" to { vm.duplicate(ids.first()); vm.clearSelection() })
                },
            )
        }
    }

    if (colorSheet) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { colorSheet = false }, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text("Color", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 24.dp, bottom = 16.dp))
            val current = all.firstOrNull { it.note.id in selection }?.note?.color ?: 0
            ColorPickerRow(current, { c -> vm.color(selection.toList(), c); colorSheet = false; vm.clearSelection() })
            Spacer(Modifier.height(36.dp))
        }
    }
}

private fun LazyStaggeredGridScope.fullLine(key: String, content: @Composable () -> Unit) =
    item(key = key, span = StaggeredGridItemSpan.FullLine) { content() }

private fun LazyStaggeredGridScope.cards(
    list: List<NoteWithMeta>,
    compact: Boolean,
    selection: Set<Long>,
    selecting: Boolean,
    open: (NoteWithMeta) -> Unit,
    vm: HomeViewModel,
    haptics: com.notecraft.app.ui.common.Haptics,
    trash: (List<Long>) -> Unit,
) {
    items(list, key = { it.note.id }, contentType = { if (it.note.isList) 1 else 0 }) { m ->
        val card: @Composable (Modifier) -> Unit = { mod ->
            NoteCard(
                item = m, compact = compact, selected = m.note.id in selection, selectionMode = selecting,
                onClick = { if (selecting) { haptics.tick(); vm.toggleSelect(m.note.id) } else open(m) },
                onLongClick = { haptics.long(); vm.toggleSelect(m.note.id) },
                modifier = mod,
            )
        }
        val base = Modifier.animateItem().sharedNote("note-${m.note.id}")
        if (compact && !selecting) SwipeCard(m, base, onPin = { haptics.confirm(); vm.pin(listOf(m.note.id), !m.note.pinned) }, onTrash = { trash(listOf(m.note.id)) }) { card(Modifier) }
        else card(base)
    }
}

@Composable
private fun SwipeCard(m: NoteWithMeta, modifier: Modifier, onPin: () -> Unit, onTrash: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { it * 0.32f })
    LaunchedEffect(state.currentValue) {
        when (state.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> { onPin(); state.snapTo(SwipeToDismissBoxValue.Settled) }
            SwipeToDismissBoxValue.EndToStart -> onTrash()
            else -> {}
        }
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            val dir = state.dismissDirection
            val color by animateColorAsState(
                when (dir) {
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary
                    SwipeToDismissBoxValue.EndToStart -> NC.extra.danger
                    else -> Color.Transparent
                }, label = "swipe"
            )
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 24.dp),
                contentAlignment = if (dir == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (dir == SwipeToDismissBoxValue.StartToEnd) Icon(if (m.note.pinned) Icons.Outlined.PushPin else Icons.Rounded.PushPin, null, tint = color)
                else if (dir == SwipeToDismissBoxValue.EndToStart) Icon(Icons.Rounded.Delete, null, tint = color)
            }
        },
    ) { content() }
}

@Composable
private fun Header(title: String, subtitle: String, layout: Int, sort: Int, onLayout: () -> Unit, onSort: (Int) -> Unit) {
    val navigator = LocalNavigator.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.padding(start = 6.dp, top = 8.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(subtitle, style = MaterialTheme.typography.labelLarge, color = NC.extra.muted, modifier = Modifier.weight(1f))
            IconButton(onClick = onLayout) {
                Icon(if (layout == Layout.GRID) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView, "Toggle layout", tint = NC.extra.muted)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More", tint = NC.extra.muted) }
                DropdownMenu(menu, { menu = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
                    Text("Sort by", style = MaterialTheme.typography.labelMedium, color = NC.extra.muted, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
                    listOf(SortBy.MODIFIED to "Last edited", SortBy.CREATED to "Date created", SortBy.TITLE to "Title").forEach { (v, l) ->
                        DropdownMenuItem(
                            text = { Text(l) }, onClick = { onSort(v); menu = false },
                            trailingIcon = { RadioButton(selected = sort == v, onClick = null) },
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = NC.extra.cardBorder)
                    DropdownMenuItem(text = { Text("Archive") }, leadingIcon = { Icon(Icons.Outlined.Archive, null) }, onClick = { menu = false; navigator.archive() })
                    DropdownMenuItem(text = { Text("Trash") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; navigator.trash() })
                    DropdownMenuItem(text = { Text("Insights") }, leadingIcon = { Icon(Icons.Outlined.Insights, null) }, onClick = { menu = false; navigator.stats() })
                }
            }
            IconButton(onClick = navigator::settings) { Icon(Icons.Outlined.Settings, "Settings", tint = NC.extra.muted) }
        }
        Text(title, style = MaterialTheme.typography.displaySmall)
    }
}

@Composable
private fun SearchPill(isNotes: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp).sharedNote("search")
            .clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, NC.extra.cardBorder, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, null, tint = NC.extra.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(if (isNotes) "Search notes, lists, tags…" else "Search everything…", color = NC.extra.muted, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TagRow(tags: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    val haptics = rememberHaptics()
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        item { TagChip("All", selected == null) { haptics.tick(); onSelect(null) } }
        items(tags, key = { it }) { t -> TagChip("#$t", selected.equals(t, true)) { haptics.tick(); onSelect(t) } }
    }
}

@Composable
private fun TagChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh, label = "chip")
    val fg by animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, label = "chipfg")
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = fg,
        modifier = Modifier.clip(CircleShape).background(bg)
            .border(1.dp, if (selected) Color.Transparent else NC.extra.cardBorder, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

data class SelAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

@Composable
fun SelectionBar(count: Int, onClose: () -> Unit, actions: List<SelAction>, more: List<Pair<String, () -> Unit>> = emptyList()) {
    var menu by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 6.dp) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Cancel selection") }
            Text("$count selected", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 4.dp))
            actions.forEach { a -> IconButton(onClick = a.onClick) { Icon(a.icon, a.label) } }
            if (more.isNotEmpty()) Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(menu, { menu = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    more.forEach { (label, act) ->
                        DropdownMenuItem(
                            text = { Text(label) }, onClick = { menu = false; act() },
                            leadingIcon = { Icon(if (label == "Select all") Icons.Rounded.SelectAll else Icons.Outlined.ContentCopy, null) }
                        )
                    }
                }
            }
        }
    }
}
