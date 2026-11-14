package com.notecraft.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.notecraft.app.AppContainer
import com.notecraft.app.data.Settings
import com.notecraft.app.data.db.NoteKind
import com.notecraft.app.ui.common.LocalContainer
import com.notecraft.app.ui.common.LocalNavAnimScope
import com.notecraft.app.ui.common.LocalSharedScope
import com.notecraft.app.ui.common.LocalSnackbar
import com.notecraft.app.ui.common.rememberHaptics
import com.notecraft.app.ui.common.sharedNote
import com.notecraft.app.ui.editor.NoteEditorScreen
import com.notecraft.app.ui.home.NotesHomeScreen
import com.notecraft.app.ui.home.SimpleCollectionScreen
import com.notecraft.app.ui.lists.ListEditorScreen
import com.notecraft.app.ui.reminders.RemindersScreen
import com.notecraft.app.ui.search.SearchScreen
import com.notecraft.app.ui.settings.SettingsScreen
import com.notecraft.app.ui.settings.StatsScreen
import com.notecraft.app.ui.theme.NC
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface NavRequest {
    data class OpenNote(val id: Long) : NavRequest
    data object NewNote : NavRequest
    data object NewList : NavRequest
    data object NewReminder : NavRequest
    data object Search : NavRequest
    data class Tab(val tab: Int) : NavRequest
}

class Auth(val authenticate: (String, String?, (Boolean) -> Unit) -> Unit, val canAuthenticate: () -> Boolean)
val LocalAuth = staticCompositionLocalOf<Auth> { error("No auth") }

object Routes {
    const val MAIN = "main"
    const val NOTE = "note/{id}?origin={origin}"
    const val LIST = "list/{id}?origin={origin}"
    const val SEARCH = "search?tag={tag}"
    const val SETTINGS = "settings"
    const val STATS = "stats"
    const val ARCHIVE = "archive"
    const val TRASH = "trash"

    fun note(id: Long, origin: String? = null) = "note/$id" + (origin?.let { "?origin=$it" } ?: "")
    fun list(id: Long, origin: String? = null) = "list/$id" + (origin?.let { "?origin=$it" } ?: "")
    fun search(tag: String? = null) = "search" + (tag?.let { "?tag=${android.net.Uri.encode(it)}" } ?: "")
    fun open(id: Long, isList: Boolean, origin: String? = null) = if (isList) list(id, origin) else note(id, origin)
}

/** Navigation actions screens can call without knowing about the controller. */
class Navigator(private val nav: NavHostController, private val onTab: (Int) -> Unit) {
    fun open(id: Long, isList: Boolean, origin: String? = null) = nav.navigate(Routes.open(id, isList, origin)) { launchSingleTop = true }
    fun newNote(origin: String? = "fab") = nav.navigate(Routes.note(-1, origin)) { launchSingleTop = true }
    fun newList(origin: String? = "fab") = nav.navigate(Routes.list(-1, origin)) { launchSingleTop = true }
    fun search(tag: String? = null) = nav.navigate(Routes.search(tag)) { launchSingleTop = true }
    fun settings() = nav.navigate(Routes.SETTINGS) { launchSingleTop = true }
    fun stats() = nav.navigate(Routes.STATS) { launchSingleTop = true }
    fun archive() = nav.navigate(Routes.ARCHIVE) { launchSingleTop = true }
    fun trash() = nav.navigate(Routes.TRASH) { launchSingleTop = true }
    fun back() { if (nav.previousBackStackEntry != null) nav.popBackStack() }
    fun tab(t: Int) { nav.popBackStack(Routes.MAIN, inclusive = false); onTab(t) }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No navigator") }

@Composable
fun AppRoot(
    container: AppContainer,
    settings: Settings,
    navRequests: Flow<NavRequest>,
    authenticate: (String, String?, (Boolean) -> Unit) -> Unit,
    canAuthenticate: () -> Boolean,
) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var reminderSignal by remember { mutableIntStateOf(0) }
    val navigator = remember(nav) { Navigator(nav) { tab = it } }
    val locked by container.lock.locked.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val backEntry by nav.currentBackStackEntryAsState()
    val onMain = backEntry?.destination?.route == Routes.MAIN

    LaunchedEffect(Unit) {
        navRequests.collect { req ->
            container.ready.first { it }
            when (req) {
                is NavRequest.OpenNote -> scope.launch {
                    val n = container.repository.get(req.id) ?: return@launch
                    nav.popBackStack(Routes.MAIN, inclusive = false)
                    navigator.open(n.id, n.kind == NoteKind.LIST)
                }
                NavRequest.NewNote -> { nav.popBackStack(Routes.MAIN, false); navigator.newNote(null) }
                NavRequest.NewList -> { nav.popBackStack(Routes.MAIN, false); navigator.newList(null) }
                NavRequest.NewReminder -> { navigator.tab(2); reminderSignal++ }
                NavRequest.Search -> { nav.popBackStack(Routes.MAIN, false); navigator.search() }
                is NavRequest.Tab -> navigator.tab(req.tab)
            }
        }
    }

    CompositionLocalProvider(
        LocalContainer provides container,
        LocalSnackbar provides snackbar,
        LocalAuth provides Auth(authenticate, canAuthenticate),
        LocalNavigator provides navigator,
    ) {
        SharedTransitionLayout(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
          Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalSharedScope provides this@SharedTransitionLayout) {
                NavHost(
                    navController = nav,
                    startDestination = Routes.MAIN,
                    enterTransition = { fadeIn(tween(240)) + slideInVertically(tween(280)) { it / 28 } },
                    exitTransition = { fadeOut(tween(160)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = { fadeOut(tween(180)) + slideOutVertically(tween(240)) { it / 28 } },
                ) {
                    composable(Routes.MAIN) {
                        WithAnim(this) { MainScreen(tab, { tab = it }, reminderSignal) }
                    }
                    composable(
                        Routes.NOTE,
                        arguments = listOf(
                            navArgument("id") { type = NavType.LongType },
                            navArgument("origin") { type = NavType.StringType; nullable = true; defaultValue = null },
                        )
                    ) { e ->
                        WithAnim(this) {
                            NoteEditorScreen(e.arguments!!.getLong("id"), e.arguments?.getString("origin"))
                        }
                    }
                    composable(
                        Routes.LIST,
                        arguments = listOf(
                            navArgument("id") { type = NavType.LongType },
                            navArgument("origin") { type = NavType.StringType; nullable = true; defaultValue = null },
                        )
                    ) { e ->
                        WithAnim(this) { ListEditorScreen(e.arguments!!.getLong("id"), e.arguments?.getString("origin")) }
                    }
                    composable(
                        Routes.SEARCH,
                        arguments = listOf(navArgument("tag") { type = NavType.StringType; nullable = true; defaultValue = null })
                    ) { e -> WithAnim(this) { SearchScreen(e.arguments?.getString("tag")) } }
                    composable(Routes.SETTINGS) { WithAnim(this) { SettingsScreen() } }
                    composable(Routes.STATS) { WithAnim(this) { StatsScreen() } }
                    composable(Routes.ARCHIVE) { WithAnim(this) { SimpleCollectionScreen(trash = false) } }
                    composable(Routes.TRASH) { WithAnim(this) { SimpleCollectionScreen(trash = true) } }
                }
            }

            SnackbarHost(
                snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding()
                    .padding(bottom = if (onMain) 84.dp else 12.dp, start = 12.dp, end = 12.dp)
            ) { data ->
                Snackbar(
                    data, shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary,
                )
            }

            AnimatedVisibility(settings.appLock && locked, enter = fadeIn(), exit = fadeOut(tween(260))) {
                LockScreen { container.lock.unlock() }
            }
          }
        }
    }
}

@Composable
private fun WithAnim(scope: AnimatedVisibilityScope, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavAnimScope provides scope, content = content)
}

@Composable
private fun MainScreen(tab: Int, onTab: (Int) -> Unit, reminderSignal: Int) {
    val navigator = LocalNavigator.current
    var barVisible by remember { mutableStateOf(true) }
    var addSignal by remember { mutableIntStateOf(0) }
    var hideBar by remember { mutableStateOf(false) }
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -8f) barVisible = false else if (available.y > 8f) barVisible = true
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(tab) { barVisible = true }
    LaunchedEffect(reminderSignal) { if (reminderSignal > 0) addSignal++ }

    Box(Modifier.fillMaxSize().nestedScroll(connection)) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "tabs",
        ) { t ->
            when (t) {
                0 -> NotesHomeScreen(kind = NoteKind.NOTE, onSelectionMode = { hideBar = it })
                1 -> NotesHomeScreen(kind = NoteKind.LIST, onSelectionMode = { hideBar = it })
                else -> RemindersScreen(addSignal = addSignal)
            }
        }
        AnimatedVisibility(
            visible = barVisible && !hideBar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it * 2 } + fadeIn(),
            exit = slideOutVertically(tween(200)) { it * 2 } + fadeOut(tween(150)),
        ) {
            BottomBar(
                tab = tab, onTab = onTab,
                onAdd = {
                    when (tab) {
                        0 -> navigator.newNote()
                        1 -> navigator.newList()
                        else -> addSignal++
                    }
                }
            )
        }
    }
}

@Composable
private fun BottomBar(tab: Int, onTab: (Int) -> Unit, onAdd: () -> Unit) {
    val haptics = rememberHaptics()
    val extra = NC.extra
    Row(
        Modifier.navigationBarsPadding().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 10.dp,
            tonalElevation = 0.dp,
            modifier = Modifier.border(1.dp, extra.cardBorder, CircleShape),
        ) {
            Row(Modifier.padding(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                NavItem("Notes", Icons.Rounded.StickyNote2, Icons.Outlined.StickyNote2, tab == 0) { haptics.tick(); onTab(0) }
                NavItem("Lists", Icons.Rounded.Checklist, Icons.Outlined.Checklist, tab == 1) { haptics.tick(); onTab(1) }
                NavItem("Reminders", Icons.Rounded.Alarm, Icons.Outlined.Alarm, tab == 2) { haptics.tick(); onTab(2) }
            }
        }
        Box(
            Modifier.sharedNote(if (tab != 2) "fab" else null)
                .size(56.dp).shadow(12.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary)
                .clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                .clickable { haptics.tap(); onAdd() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "New", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun NavItem(label: String, selectedIcon: ImageVector, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent
    Row(
        Modifier.clip(CircleShape).background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (selected) selectedIcon else icon, contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.primary else NC.extra.muted, modifier = Modifier.size(22.dp)
        )
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    val auth = LocalAuth.current
    val prompt = { auth.authenticate("Unlock NoteCraft", null) { ok -> if (ok) onUnlock() } }
    LaunchedEffect(Unit) { prompt() }
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) }
            Spacer(Modifier.height(20.dp))
            Text("NoteCraft is locked", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text("Your notes are safe and private.", color = NC.extra.muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(28.dp))
            Button(onClick = prompt, shape = CircleShape) { Text("Unlock") }
        }
    }
}
