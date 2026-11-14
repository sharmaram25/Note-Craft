package com.notecraft.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.notecraft.app.AppIntents
import com.notecraft.app.MainActivity
import com.notecraft.app.NoteCraftApp
import com.notecraft.app.data.db.NoteWithMeta
import com.notecraft.app.domain.TextTools
import com.notecraft.app.ui.theme.NoteColors

private val bg = ColorProvider(day = Color(0xFFF6F4EF), night = Color(0xFF131312))
private val card = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1D1C1A))
private val ink = ColorProvider(day = Color(0xFF1C1B19), night = Color(0xFFEDEBE6))
private val muted = ColorProvider(day = Color(0xFF6F6A62), night = Color(0xFFA29E97))
private val brand = ColorProvider(day = Color(0xFF2F6BEA), night = Color(0xFF8AB0FF))
private val onBrand = ColorProvider(day = Color.White, night = Color(0xFF111111))

class NotesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = (context.applicationContext as NoteCraftApp).container.repository
        val cards = repo.widgetCards(10)
        provideContent { Content(context, cards) }
    }

    @Composable
    private fun Content(context: Context, cards: List<NoteWithMeta>) {
        Column(GlanceModifier.fillMaxSize().background(bg).cornerRadius(24.dp).padding(12.dp)) {
            Row(GlanceModifier.fillMaxWidth().padding(start = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("NoteCraft", style = TextStyle(color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(openIntent(context, null))))
                Box(
                    GlanceModifier.size(32.dp).background(card).cornerRadius(16.dp)
                        .clickable(actionStartActivity(actionIntent(context, AppIntents.ACTION_NEW_LIST))),
                    contentAlignment = Alignment.Center
                ) { Text("☑", style = TextStyle(color = brand, fontSize = 15.sp)) }
                Spacer(GlanceModifier.width(8.dp))
                Box(
                    GlanceModifier.size(32.dp).background(brand).cornerRadius(16.dp)
                        .clickable(actionStartActivity(actionIntent(context, AppIntents.ACTION_NEW_NOTE))),
                    contentAlignment = Alignment.Center
                ) { Text("+", style = TextStyle(color = onBrand, fontSize = 20.sp, fontWeight = FontWeight.Bold)) }
            }
            if (cards.isEmpty()) {
                Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Tap + to capture a thought", style = TextStyle(color = muted, fontSize = 13.sp))
                }
            } else {
                LazyColumn {
                    items(cards, itemId = { it.note.id }) { c ->
                        Column(GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            NoteRow(context, c)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun NoteRow(context: Context, c: NoteWithMeta) {
        val n = c.note
        val tint = NoteColors.getOrNull(n.color)?.takeIf { n.color != 0 }
        val rowBg = if (tint != null) ColorProvider(day = tint.light, night = tint.dark) else card
        val preview = if (n.isList) {
            val (done, total) = TextTools.taskProgress(n.body)
            if (total == 0) "Empty list" else "$done of $total done · " + TextTools.parseTasks(n.body).filter { !it.checked }.take(3).joinToString(", ") { it.text }
        } else TextTools.plainPreview(n.body, 160).replace('\n', ' ')
        Column(
            GlanceModifier.fillMaxWidth().background(rowBg).cornerRadius(16.dp).padding(horizontal = 12.dp, vertical = 10.dp)
                .clickable(actionStartActivity(openIntent(context, n.id)))
        ) {
            val title = n.title.ifBlank { preview.take(60) }
            Text((if (n.pinned) "• " else "") + title.ifBlank { "Untitled" }, maxLines = 1,
                style = TextStyle(color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium))
            if (n.title.isNotBlank() && preview.isNotBlank()) {
                Spacer(GlanceModifier.height(2.dp))
                Text(preview, maxLines = 2, style = TextStyle(color = muted, fontSize = 12.sp))
            }
        }
    }

    private fun openIntent(context: Context, id: Long?) = Intent(context, MainActivity::class.java).apply {
        action = AppIntents.ACTION_OPEN
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        data = Uri.parse("notecraft://note/${id ?: 0}")
        if (id != null) putExtra(AppIntents.EXTRA_NOTE_ID, id)
    }

    private fun actionIntent(context: Context, action: String) = Intent(context, MainActivity::class.java).apply {
        this.action = action
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    companion object {
        suspend fun refresh(context: Context) = runCatching { NotesWidget().updateAll(context) }
    }
}

class NotesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NotesWidget()
}
