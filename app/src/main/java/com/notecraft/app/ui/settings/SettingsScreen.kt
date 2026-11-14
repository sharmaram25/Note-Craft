package com.notecraft.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notecraft.app.BuildConfig
import com.notecraft.app.data.EditorFont
import com.notecraft.app.data.ThemeMode
import com.notecraft.app.ui.LocalAuth
import com.notecraft.app.ui.LocalNavigator
import com.notecraft.app.ui.common.ConfirmDialog
import com.notecraft.app.ui.common.LocalContainer
import com.notecraft.app.ui.common.rememberHaptics
import com.notecraft.app.ui.theme.Accents
import com.notecraft.app.ui.theme.NC
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SettingsScreen() {
    val container = LocalContainer.current
    val store = container.settings
    val s by store.settings.collectAsState(initial = null)
    val settings = s ?: return
    val navigator = LocalNavigator.current
    val auth = LocalAuth.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val dark = com.notecraft.app.ui.theme.isAppDark(settings)
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    fun run(label: String, block: suspend () -> String) {
        busy = true
        scope.launch {
            val msg = runCatching { block() }.getOrElse { "$label failed: ${it.message ?: "unknown error"}" }
            busy = false
            toast(msg)
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { run("Backup") { val r = container.backup.exportBackup(it); "Backed up ${r.notes} notes" } }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }
    val mdExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { run("Export") { val n = container.backup.exportMarkdown(it); "Exported $n notes as Markdown" } }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) run("Import") { val n = container.backup.importTextFiles(uris); "Imported $n ${if (n == 1) "note" else "notes"}" }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = navigator::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        }
        Text("Settings", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))

        Group("Appearance") {
            Label(Icons.Outlined.DarkMode, "Theme")
            Segmented(listOf("System", "Light", "Dark"), settings.themeMode) { haptics.tick(); scope.launch { store.setThemeMode(it) } }
            AnimatedVisibility(dark) {
                ToggleRow(Icons.Outlined.Wallpaper, "True black", "Pure black backgrounds for OLED screens", settings.amoled) { scope.launch { store.setAmoled(it) } }
            }
            if (Build.VERSION.SDK_INT >= 31) ToggleRow(Icons.Outlined.Palette, "Match wallpaper colors", "Use Material You colors from your wallpaper", settings.dynamicColor) {
                scope.launch { store.setDynamicColor(it) }
            }
            AnimatedVisibility(!settings.dynamicColor) {
                Column {
                    Label(null, "Accent")
                    FlowRow(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Accents.forEachIndexed { i, a ->
                            val c = if (dark) a.dark else a.light
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { haptics.tick(); scope.launch { store.setAccent(i) } }) {
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape).background(c)
                                        .border(if (settings.accent == i) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) { if (settings.accent == i) Icon(Icons.Rounded.Check, null, tint = if (dark) Color.Black else Color.White, modifier = Modifier.size(18.dp)) }
                                Spacer(Modifier.height(4.dp))
                                Text(a.name, style = MaterialTheme.typography.labelSmall, color = NC.extra.muted)
                            }
                        }
                    }
                }
            }
        }

        Group("Writing") {
            Label(Icons.Outlined.SortByAlpha, "Font")
            Segmented(listOf("Sans", "Serif", "Mono"), settings.editorFont) { haptics.tick(); scope.launch { store.setEditorFont(it) } }
            var size by remember(settings.editorFontSize) { mutableFloatStateOf(settings.editorFontSize.toFloat()) }
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Text size", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("${size.toInt()}", style = MaterialTheme.typography.labelLarge, color = NC.extra.muted)
            }
            Slider(
                value = size, onValueChange = { size = it; haptics.tick() }, valueRange = 13f..26f, steps = 12,
                onValueChangeFinished = { scope.launch { store.setEditorFontSize(size.toInt()) } },
                colors = SliderDefaults.colors(inactiveTickColor = Color.Transparent, activeTickColor = Color.Transparent),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                "The quick brown fox jots down a thought.",
                fontSize = size.sp, lineHeight = (size * 1.5f).sp,
                fontFamily = when (settings.editorFont) { EditorFont.SERIF -> FontFamily.Serif; EditorFont.MONO -> FontFamily.Monospace; else -> FontFamily.Default },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface).padding(14.dp)
            )
            ToggleRow(Icons.Outlined.AutoStories, "Open notes in reading mode", "Tap the pencil to edit", settings.openInReadMode) { scope.launch { store.setOpenInReadMode(it) } }
            ToggleRow(Icons.Outlined.Checklist, "Move checked items to bottom", "Keeps lists focused on what's left", settings.moveCheckedToBottom) { scope.launch { store.setMoveCheckedToBottom(it) } }
            ToggleRow(Icons.Outlined.Vibration, "Haptic feedback", "Subtle taps for meaningful actions", settings.haptics) { scope.launch { store.setHaptics(it) } }
        }

        Group("Privacy & security") {
            ToggleRow(Icons.Outlined.Fingerprint, "App lock", "Require fingerprint, face or screen lock to open", settings.appLock) { enable ->
                if (!auth.canAuthenticate()) { toast("Set up a screen lock on your device first"); return@ToggleRow }
                auth.authenticate(if (enable) "Turn on app lock" else "Turn off app lock", null) { ok ->
                    if (ok) scope.launch { store.setAppLock(enable); if (enable) container.lock.unlock() else container.lock.setDisabled() }
                }
            }
            AnimatedVisibility(settings.appLock) {
                Column {
                    Label(null, "Lock after leaving the app")
                    val options = listOf(0 to "Immediately", 30 to "30 sec", 60 to "1 min", 300 to "5 min")
                    Segmented(options.map { it.second }, options.indexOfFirst { it.first == settings.lockTimeoutSec }.coerceAtLeast(0)) {
                        scope.launch { store.setLockTimeout(options[it].first) }
                    }
                }
            }
            ToggleRow(Icons.Outlined.VisibilityOff, "Hide content in recents", "Blocks screenshots and blurs the app switcher", settings.secureScreen) { scope.launch { store.setSecureScreen(it) } }
            InfoRow(Icons.Outlined.Shield, "Private by design", "Everything stays on this device. No accounts, no tracking, no ads.")
        }

        Group("Reminders") {
            val exact = container.scheduler.canScheduleExact()
            NavRow(Icons.Outlined.Alarm, "Precise timing", if (exact) "On — reminders arrive exactly on time" else "Off — reminders may be a few minutes late") {
                if (Build.VERSION.SDK_INT >= 31) runCatching {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }
            }
            NavRow(Icons.Outlined.Notifications, "Notification settings", "Sound, vibration and lock-screen display") {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
        }

        Group("Your data") {
            NavRow(Icons.Outlined.Backup, "Back up", "Save everything — notes, lists, images, reminders — to a file") {
                backupLauncher.launch("NoteCraft-backup-${LocalDate.now()}.zip")
            }
            NavRow(Icons.Outlined.Restore, "Restore from backup", "Adds notes from a backup file; nothing is overwritten") {
                restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"))
            }
            NavRow(Icons.Outlined.Upload, "Export as Markdown", "A .zip of plain .md files you can open anywhere") {
                mdExportLauncher.launch("NoteCraft-markdown-${LocalDate.now()}.zip")
            }
            NavRow(Icons.Outlined.Description, "Import text files", "Bring in .md or .txt files as notes") {
                importLauncher.launch(arrayOf("text/*"))
            }
            NavRow(Icons.Outlined.Archive, "Archive", null) { navigator.archive() }
            NavRow(Icons.Outlined.DeleteOutline, "Trash", null) { navigator.trash() }
            NavRow(Icons.Outlined.Insights, "Insights", "Your writing streaks and habits") { navigator.stats() }
        }

        Group("About") {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("NoteCraft", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = NC.extra.muted)
                Spacer(Modifier.height(10.dp))
                Text("Crafted by Ram Sharma", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            NavRow(Icons.AutoMirrored.Outlined.OpenInNew, "GitHub profile", "github.com/sharmaram25") {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sharmaram25")))
            }
            NavRow(Icons.Outlined.Code, "Source code", "github.com/sharmaram25/Note-Craft") {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sharmaram25/Note-Craft")))
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (busy) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)).clickable(enabled = true) {})
    restoreUri?.let { uri ->
        ConfirmDialog(
            "Restore from backup?", "Notes from the backup will be added alongside your current notes.", "Restore", destructive = false,
            onConfirm = { run("Restore") { val r = container.backup.importBackup(uri); "Restored ${r.notes} notes and ${r.reminders} reminders" } },
            onDismiss = { restoreUri = null },
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, top = 22.dp, bottom = 8.dp))
    Column(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest).border(1.dp, NC.extra.cardBorder, RoundedCornerShape(22.dp))
            .padding(vertical = 8.dp),
        content = content
    )
}

@Composable
private fun Label(icon: ImageVector?, text: String) {
    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = NC.extra.muted, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(16.dp)) }
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Text(
                label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge,
                color = if (sel) MaterialTheme.colorScheme.onSurface else NC.extra.muted,
                modifier = Modifier.weight(1f).clip(CircleShape)
                    .background(if (sel) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = NC.extra.muted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NC.extra.muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(uncheckedBorderColor = NC.extra.cardBorder))
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = NC.extra.muted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NC.extra.muted)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = NC.extra.faint)
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = NC.extra.success, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NC.extra.muted)
        }
    }
}
