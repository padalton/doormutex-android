package net.padalton.doormutex.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.padalton.doormutex.android.BuildConfig
import net.padalton.doormutex.android.R
import net.padalton.doormutex.android.data.Light
import net.padalton.doormutex.android.ui.TrafficLight

private const val REPO_HUB = "https://github.com/padalton/doormutex"
private const val REPO_APP = "https://github.com/padalton/doormutex-android"

private data class Link(
    val label: Int,
    val url: String,
)

/** Dedication, version, and every link worth having: website, sources, help, legal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onLicences: () -> Unit,
) {
    val uri = LocalUriHandler.current
    val links =
        listOf(
            Link(R.string.about_website, stringResource(R.string.url_website)),
            Link(R.string.about_get_started, stringResource(R.string.url_get_started)),
            Link(R.string.about_hardware, stringResource(R.string.url_hardware)),
            Link(R.string.about_source_hub, REPO_HUB),
            Link(R.string.about_source_app, REPO_APP),
            Link(R.string.about_issues, "$REPO_APP/issues"),
            Link(R.string.about_privacy, "$REPO_APP/blob/main/PRIVACY.md"),
            Link(R.string.about_legal, stringResource(R.string.url_legal)),
        )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("about"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Dedication() }
            item { AppHeader() }
            item {
                Text(
                    stringResource(R.string.about_links),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                )
            }
            items(links) { link ->
                ListItem(
                    headlineContent = { Text(stringResource(link.label)) },
                    supportingContent = { Text(link.url.removePrefix("https://"), style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.fillMaxWidth().clickable { uri.openUri(link.url) },
                )
            }
            item { HorizontalDivider() }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_licences)) },
                    supportingContent = { Text(stringResource(R.string.about_licence), style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onLicences),
                )
            }
        }
    }
}

/** First thing on the page, on purpose. */
@Composable
private fun Dedication() {
    // Its own warm rose instead of the dynamic palette: the same in every theme, and a red heart.
    val dark = isSystemInDarkTheme()
    val container = if (dark) Color(0xFF4A1F2E) else Color(0xFFFCE4EC)
    val content = if (dark) Color(0xFFFFD9E2) else Color(0xFF3E0A1C)
    val heart = if (dark) Color(0xFFFF8FAB) else Color(0xFFD81B60)
    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        modifier = Modifier.fillMaxWidth().testTag("dedication"),
    ) {
        Column(
            Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_favorite),
                contentDescription = null,
                tint = heart,
                modifier = Modifier.size(40.dp),
            )
            Text(
                stringResource(R.string.about_dedication_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.about_dedication_text),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AppHeader() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrafficLight(Light.RED, height = 56.dp)
        Column {
            Text(stringResource(R.string.about_app), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
