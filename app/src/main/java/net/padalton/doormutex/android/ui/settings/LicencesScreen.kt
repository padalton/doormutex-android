package net.padalton.doormutex.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.padalton.doormutex.android.R

private data class Component(
    val name: String,
    val by: String,
    val licence: String,
)

/** Everything shipped in the APK, with its licence. Keep in sync with gradle/libs.versions.toml. */
private val COMPONENTS =
    listOf(
        Component("doormutex for Android", "padalton – github.com/padalton/doormutex-android", "Apache-2.0"),
        Component(
            "Android Jetpack: Compose, Material 3, Glance, Navigation, Lifecycle, WorkManager, DataStore, CameraX",
            "The Android Open Source Project",
            "Apache-2.0",
        ),
        Component("Kotlin standard library, kotlinx.coroutines, kotlinx.serialization", "JetBrains s.r.o.", "Apache-2.0"),
        Component("OkHttp, Okio", "Square, Inc.", "Apache-2.0"),
        Component("ZXing", "ZXing authors", "Apache-2.0"),
        Component("Material Symbols (settings, back)", "Google LLC", "Apache-2.0"),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicencesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.licences_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(COMPONENTS) {
                Column {
                    Text(it.name, style = MaterialTheme.typography.titleSmall)
                    Text(it.by, style = MaterialTheme.typography.bodyMedium)
                    Text(it.licence, style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Text(
                    "Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
