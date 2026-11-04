package net.padalton.doormutex.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.flow.first
import net.padalton.doormutex.android.MainActivity
import net.padalton.doormutex.android.R
import net.padalton.doormutex.android.data.Freshness
import net.padalton.doormutex.android.data.ProfileInfo
import net.padalton.doormutex.android.data.Snapshot
import net.padalton.doormutex.android.repository
import net.padalton.doormutex.android.ui.describe
import net.padalton.doormutex.android.ui.statusText
import net.padalton.doormutex.android.ui.untilText

/**
 * All lights, 3×2 and up: one row per profile with lamp, name, status and
 * until when. Tapping a row opens the app at that profile.
 */
class AllLightsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val repository = context.repository
        val initial = repository.snapshot.first()
        provideContent {
            val snapshot by repository.snapshot.collectAsState(initial)
            GlanceTheme { AllLights(snapshot, System.currentTimeMillis()) }
        }
    }
}

class AllLightsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AllLightsWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedule(context)
    }
}

@Composable
private fun AllLights(
    snapshot: Snapshot,
    now: Long,
) {
    val context = LocalContext.current
    val root =
        GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)

    if (snapshot.profiles.isEmpty()) {
        Column(
            root.clickable(actionStartActivity(MainActivity.intent(context, null))),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(context.getString(R.string.widget_not_paired), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp))
        }
        return
    }

    val freshness = Freshness.of(snapshot, now, RefreshWorker.INTERVAL)
    Column(root) {
        if (freshness.stale) {
            Text(
                staleText(context, snapshot, freshness),
                style = TextStyle(color = GlanceTheme.colors.error, fontSize = 11.sp),
                maxLines = 1,
                modifier = GlanceModifier.padding(bottom = 4.dp),
            )
        }
        LazyColumn(GlanceModifier.fillMaxWidth()) {
            items(snapshot.profiles, itemId = { it.id.hashCode().toLong() }) { profile ->
                Column {
                    ProfileRow(profile, freshness.stale)
                    Spacer(GlanceModifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(
    profile: ProfileInfo,
    stale: Boolean,
) {
    val context = LocalContext.current
    val main = if (stale) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface
    val a11y = context.describe(profile)
    Row(
        GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity(MainActivity.intent(context, profile.id)))
            .semantics { contentDescription = a11y },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Lamp(profile.state.light, dimmed = stale, height = 40.dp)
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(profile.name, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp), maxLines = 1)
            Text(
                context.statusText(profile.state) + if (profile.state.light.blinks) " ◌" else "",
                style = TextStyle(color = main, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Text(context.untilText(profile.state), style = TextStyle(color = main, fontSize = 11.sp), maxLines = 1)
        }
    }
}

// Previews (Android Studio → Split/Design view).

@OptIn(androidx.glance.preview.ExperimentalGlancePreviewApi::class)
@androidx.glance.preview.Preview(widthDp = 250, heightDp = 160)
@Composable
private fun PreviewAll() = GlanceTheme { AllLights(previewSnapshots(stale = false), System.currentTimeMillis()) }

@OptIn(androidx.glance.preview.ExperimentalGlancePreviewApi::class)
@androidx.glance.preview.Preview(widthDp = 250, heightDp = 160)
@Composable
private fun PreviewAllStale() = GlanceTheme { AllLights(previewSnapshots(stale = true), System.currentTimeMillis()) }
