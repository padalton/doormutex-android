package net.padalton.doormutex.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.flow.first
import net.padalton.doormutex.android.MainActivity
import net.padalton.doormutex.android.R
import net.padalton.doormutex.android.data.Freshness
import net.padalton.doormutex.android.data.Light
import net.padalton.doormutex.android.data.Problem
import net.padalton.doormutex.android.data.ProfileInfo
import net.padalton.doormutex.android.data.Snapshot
import net.padalton.doormutex.android.repository
import net.padalton.doormutex.android.ui.LampColors
import net.padalton.doormutex.android.ui.describe
import net.padalton.doormutex.android.ui.statusText
import net.padalton.doormutex.android.ui.time
import net.padalton.doormutex.android.ui.untilText

/** Per-instance setting: which profile this widget shows. */
val PROFILE_KEY = stringPreferencesKey("profile")

/**
 * Single light, 1×1 up to 2×2: the lamp, and from 2×1 up the status and
 * until when. Tapping opens the app at this profile, which refreshes.
 */
class SingleLightWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, LARGE))

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val repository = context.repository
        val initial = repository.snapshot.first()
        provideContent {
            val snapshot by repository.snapshot.collectAsState(initial)
            val chosen = currentState<Preferences>()[PROFILE_KEY]
            GlanceTheme { SingleLight(snapshot, chosen, System.currentTimeMillis()) }
        }
    }

    companion object {
        val SMALL = DpSize(57.dp, 57.dp)
        val WIDE = DpSize(130.dp, 57.dp)
        val LARGE = DpSize(130.dp, 130.dp)
    }
}

class SingleLightWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SingleLightWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedule(context)
    }
}

@Composable
private fun SingleLight(
    snapshot: Snapshot,
    chosen: String?,
    now: Long,
) {
    val context = LocalContext.current
    val profile = snapshot.profiles.firstOrNull { it.id == chosen } ?: snapshot.profiles.firstOrNull()
    val root =
        GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(8.dp)
            .clickable(actionStartActivity(MainActivity.intent(context, profile?.id)))

    if (profile == null) {
        Column(root, verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(context.getString(R.string.widget_not_paired), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp))
        }
        return
    }

    val freshness = Freshness.of(snapshot, now, RefreshWorker.INTERVAL)
    val size = LocalSize.current
    val wide = size.width >= SingleLightWidget.WIDE.width
    val tall = size.height >= SingleLightWidget.LARGE.height
    val a11y = context.describe(profile) + staleSuffix(context, snapshot, freshness)

    Row(
        root.semantics { contentDescription = a11y },
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        Lamp(profile.state.light, dimmed = freshness.stale, height = if (tall) 96.dp else 48.dp)
        if (wide) {
            Spacer(GlanceModifier.width(10.dp))
            Texts(profile, freshness, snapshot, tall)
        }
    }
}

@Composable
private fun Texts(
    profile: ProfileInfo,
    freshness: Freshness,
    snapshot: Snapshot,
    tall: Boolean,
) {
    val context = LocalContext.current
    val main = if (freshness.stale) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface
    Column {
        if (tall) Text(profile.name, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
        Text(
            context.statusText(profile.state),
            style = TextStyle(color = main, fontSize = if (tall) 18.sp else 14.sp, fontWeight = FontWeight.Bold),
            maxLines = 2,
        )
        Text(context.untilText(profile.state), style = TextStyle(color = main, fontSize = 12.sp), maxLines = 1)
        if (profile.state.light.blinks) {
            Text("◌ " + context.getString(R.string.blinking), style = TextStyle(color = main, fontSize = 11.sp), maxLines = 1)
        }
        if (freshness.stale) {
            Text(
                staleText(context, snapshot, freshness),
                style = TextStyle(color = GlanceTheme.colors.error, fontSize = 11.sp),
                maxLines = 2,
            )
        }
    }
}

private fun staleText(
    context: Context,
    snapshot: Snapshot,
    freshness: Freshness,
): String {
    val since = freshness.since?.let { context.getString(R.string.since, context.time(it)) }
    val problem =
        when (snapshot.problem) {
            Problem.UNREACHABLE -> context.getString(R.string.hub_unreachable)
            Problem.UNAUTHORIZED -> context.getString(R.string.token_rejected)
            else -> null
        }
    return listOfNotNull(problem, since).joinToString(" · ")
}

private fun staleSuffix(
    context: Context,
    snapshot: Snapshot,
    freshness: Freshness,
) = if (freshness.stale) ". " + staleText(context, snapshot, freshness) else ""

/** Widgets cannot animate cheaply: blinking lamps are drawn lit, with a text marker. */
@Composable
private fun Lamp(
    light: Light,
    dimmed: Boolean,
    height: androidx.compose.ui.unit.Dp,
) {
    val lamp = height / 3.6f
    Column(
        GlanceModifier
            .background(Color(LampColors.HOUSING))
            .cornerRadius(lamp / 2)
            .padding(horizontal = lamp * 0.15f, vertical = lamp * 0.2f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        listOf(light.red to LampColors.RED, light.yellow to LampColors.YELLOW, light.green to LampColors.GREEN)
            .forEachIndexed { i, (on, color) ->
                if (i > 0) Spacer(GlanceModifier.height(lamp * 0.1f))
                val c =
                    if (!on) {
                        LampColors.UNLIT
                    } else if (dimmed) {
                        dim(color)
                    } else {
                        color
                    }
                Image(
                    provider = ImageProvider(R.drawable.lamp),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(ColorProvider(Color(c))),
                    modifier = GlanceModifier.size(lamp),
                )
            }
    }
}

/** Halfway towards the unlit colour: still recognisable, clearly not live. */
private fun dim(color: Long): Long {
    fun ch(
        c: Long,
        shift: Int,
    ) = (c shr shift) and 0xFF
    val mixed =
        listOf(16, 8, 0).fold(0xFF000000) { acc, shift ->
            acc or (((ch(color, shift) + ch(LampColors.UNLIT, shift)) / 2) shl shift)
        }
    return mixed
}

// Previews for every size (Android Studio → Split/Design view).

private fun previewSnapshot(stale: Boolean): Snapshot {
    val now = java.time.OffsetDateTime.now()
    val state =
        net.padalton.doormutex.android.data.State(
            status = net.padalton.doormutex.android.data.Status.DND,
            light = Light.RED_YELLOW,
            reason = net.padalton.doormutex.android.data.Reason.CALENDAR,
            detail = "video_call",
            since = now.minusMinutes(20),
            until = now.plusMinutes(10),
            updated = now,
        )
    val fetched = System.currentTimeMillis() - if (stale) 3_600_000 else 0
    return Snapshot(
        profiles = listOf(ProfileInfo("office", "Office", "office", state)),
        fetchedAt = fetched,
        attemptedAt = System.currentTimeMillis(),
        problem = if (stale) Problem.UNREACHABLE else null,
    )
}

@OptIn(androidx.glance.preview.ExperimentalGlancePreviewApi::class)
@androidx.glance.preview.Preview(widthDp = 57, heightDp = 57)
@Composable
private fun PreviewSmall() = GlanceTheme { SingleLight(previewSnapshot(false), null, System.currentTimeMillis()) }

@OptIn(androidx.glance.preview.ExperimentalGlancePreviewApi::class)
@androidx.glance.preview.Preview(widthDp = 130, heightDp = 57)
@Composable
private fun PreviewWide() = GlanceTheme { SingleLight(previewSnapshot(false), null, System.currentTimeMillis()) }

@OptIn(androidx.glance.preview.ExperimentalGlancePreviewApi::class)
@androidx.glance.preview.Preview(widthDp = 130, heightDp = 130)
@Composable
private fun PreviewLarge() = GlanceTheme { SingleLight(previewSnapshot(false), null, System.currentTimeMillis()) }

@OptIn(androidx.glance.preview.ExperimentalGlancePreviewApi::class)
@androidx.glance.preview.Preview(widthDp = 130, heightDp = 130)
@Composable
private fun PreviewLargeStale() = GlanceTheme { SingleLight(previewSnapshot(true), null, System.currentTimeMillis()) }
