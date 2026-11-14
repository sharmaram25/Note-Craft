package com.notecraft.app.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

object ThemeMode { const val SYSTEM = 0; const val LIGHT = 1; const val DARK = 2 }
object EditorFont { const val SANS = 0; const val SERIF = 1; const val MONO = 2 }
object Layout { const val GRID = 0; const val LIST = 1 }
object SortBy { const val MODIFIED = 0; const val CREATED = 1; const val TITLE = 2 }

@Immutable
data class Settings(
    val themeMode: Int = ThemeMode.SYSTEM,
    val amoled: Boolean = false,
    val dynamicColor: Boolean = false,
    val accent: Int = 0,
    val editorFont: Int = EditorFont.SANS,
    val editorFontSize: Int = 17,
    val notesLayout: Int = Layout.GRID,
    val listsLayout: Int = Layout.GRID,
    val sort: Int = SortBy.MODIFIED,
    val openInReadMode: Boolean = false,
    val appLock: Boolean = false,
    val lockTimeoutSec: Int = 30,
    val secureScreen: Boolean = false,
    val moveCheckedToBottom: Boolean = true,
    val haptics: Boolean = true,
    val recentSearches: List<String> = emptyList(),
    val onboarded: Boolean = false,
    val legacyMigrated: Boolean = false,
)

class SettingsStore(private val context: Context) {

    private object K {
        val themeMode = intPreferencesKey("theme_mode")
        val amoled = booleanPreferencesKey("amoled")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val accent = intPreferencesKey("accent")
        val editorFont = intPreferencesKey("editor_font")
        val editorFontSize = intPreferencesKey("editor_font_size")
        val notesLayout = intPreferencesKey("notes_layout")
        val listsLayout = intPreferencesKey("lists_layout")
        val sort = intPreferencesKey("sort")
        val openInReadMode = booleanPreferencesKey("open_read_mode")
        val appLock = booleanPreferencesKey("app_lock")
        val lockTimeout = intPreferencesKey("lock_timeout")
        val secureScreen = booleanPreferencesKey("secure_screen")
        val moveChecked = booleanPreferencesKey("move_checked")
        val haptics = booleanPreferencesKey("haptics")
        val recentSearches = stringPreferencesKey("recent_searches")
        val onboarded = booleanPreferencesKey("onboarded")
        val legacyMigrated = booleanPreferencesKey("legacy_migrated")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }.distinctUntilChanged()

    suspend fun current(): Settings = settings.first()

    private fun Preferences.toSettings() = Settings(
        themeMode = this[K.themeMode] ?: ThemeMode.SYSTEM,
        amoled = this[K.amoled] ?: false,
        dynamicColor = this[K.dynamicColor] ?: false,
        accent = this[K.accent] ?: 0,
        editorFont = this[K.editorFont] ?: EditorFont.SANS,
        editorFontSize = this[K.editorFontSize] ?: 17,
        notesLayout = this[K.notesLayout] ?: Layout.GRID,
        listsLayout = this[K.listsLayout] ?: Layout.GRID,
        sort = this[K.sort] ?: SortBy.MODIFIED,
        openInReadMode = this[K.openInReadMode] ?: false,
        appLock = this[K.appLock] ?: false,
        lockTimeoutSec = this[K.lockTimeout] ?: 30,
        secureScreen = this[K.secureScreen] ?: false,
        moveCheckedToBottom = this[K.moveChecked] ?: true,
        haptics = this[K.haptics] ?: true,
        recentSearches = this[K.recentSearches]?.split('\u001F')?.filter { it.isNotBlank() } ?: emptyList(),
        onboarded = this[K.onboarded] ?: false,
        legacyMigrated = this[K.legacyMigrated] ?: false,
    )

    private suspend fun set(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    suspend fun setThemeMode(v: Int) = set { it[K.themeMode] = v }
    suspend fun setAmoled(v: Boolean) = set { it[K.amoled] = v }
    suspend fun setDynamicColor(v: Boolean) = set { it[K.dynamicColor] = v }
    suspend fun setAccent(v: Int) = set { it[K.accent] = v }
    suspend fun setEditorFont(v: Int) = set { it[K.editorFont] = v }
    suspend fun setEditorFontSize(v: Int) = set { it[K.editorFontSize] = v.coerceIn(13, 26) }
    suspend fun setNotesLayout(v: Int) = set { it[K.notesLayout] = v }
    suspend fun setListsLayout(v: Int) = set { it[K.listsLayout] = v }
    suspend fun setSort(v: Int) = set { it[K.sort] = v }
    suspend fun setOpenInReadMode(v: Boolean) = set { it[K.openInReadMode] = v }
    suspend fun setAppLock(v: Boolean) = set { it[K.appLock] = v }
    suspend fun setLockTimeout(v: Int) = set { it[K.lockTimeout] = v }
    suspend fun setSecureScreen(v: Boolean) = set { it[K.secureScreen] = v }
    suspend fun setMoveCheckedToBottom(v: Boolean) = set { it[K.moveChecked] = v }
    suspend fun setHaptics(v: Boolean) = set { it[K.haptics] = v }
    suspend fun setOnboarded() = set { it[K.onboarded] = true }
    suspend fun setLegacyMigrated() = set { it[K.legacyMigrated] = true }

    suspend fun addRecentSearch(q: String) = set { prefs ->
        val query = q.trim()
        if (query.length < 2) return@set
        val current = prefs[K.recentSearches]?.split('\u001F')?.filter { it.isNotBlank() } ?: emptyList()
        val next = (listOf(query) + current.filterNot { it.equals(query, true) }).take(8)
        prefs[K.recentSearches] = next.joinToString("\u001F")
    }

    suspend fun clearRecentSearches() = set { it.remove(K.recentSearches) }
}
