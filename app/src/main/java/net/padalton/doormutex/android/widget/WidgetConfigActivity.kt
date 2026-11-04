package net.padalton.doormutex.android.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.padalton.doormutex.android.R
import net.padalton.doormutex.android.data.Snapshot
import net.padalton.doormutex.android.repository
import net.padalton.doormutex.android.ui.TrafficLight
import net.padalton.doormutex.android.ui.statusText
import net.padalton.doormutex.android.ui.theme.DoormutexTheme

/** Asks which profile a new (or reconfigured) single-light widget shows. */
class WidgetConfigActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        // Leaving without a choice must not place the widget.
        setResult(RESULT_CANCELED, result(widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            DoormutexTheme {
                val snapshot by repository.snapshot.collectAsStateWithLifecycle(Snapshot())
                Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.widget_pick_profile)) }) }) { padding ->
                    LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                        if (snapshot.profiles.isEmpty()) {
                            item { Text(stringResource(R.string.widget_no_hub), Modifier.padding(16.dp)) }
                        }
                        items(snapshot.profiles, key = { it.id }) { profile ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { choose(widgetId, profile.id) }
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TrafficLight(profile.state.light, height = 40.dp)
                                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                                Text(statusText(profile.state), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun choose(
        widgetId: Int,
        profile: String,
    ) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(widgetId)
            updateAppWidgetState(this@WidgetConfigActivity, glanceId) { it[PROFILE_KEY] = profile }
            SingleLightWidget().update(this@WidgetConfigActivity, glanceId)
            setResult(RESULT_OK, result(widgetId))
            finish()
        }
    }

    private fun result(widgetId: Int) = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}
