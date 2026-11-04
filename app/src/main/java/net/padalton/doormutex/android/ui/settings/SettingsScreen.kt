package net.padalton.doormutex.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.padalton.doormutex.android.BuildConfig
import net.padalton.doormutex.android.R
import net.padalton.doormutex.android.data.Role
import net.padalton.doormutex.android.live.LiveService
import net.padalton.doormutex.android.repository
import net.padalton.doormutex.android.ui.rememberNotificationRequest
import net.padalton.doormutex.android.widget.redrawWidgets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onForgotten: () -> Unit,
    onReplace: (url: String) -> Unit,
    onLicences: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by context.repository.config.collectAsStateWithLifecycle(null)
    val me by context.repository.me.collectAsStateWithLifecycle(null)
    val live by context.repository.liveMode.collectAsStateWithLifecycle(false)
    val notifications = rememberNotificationRequest()

    fun setLive(on: Boolean) =
        scope.launch {
            context.repository.setLiveMode(on)
            LiveService.sync(context)
        }
    var confirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_hub), style = MaterialTheme.typography.labelLarge)
            Text(config?.url.orEmpty())
            Text(stringResource(R.string.settings_token), style = MaterialTheme.typography.labelLarge)
            // The token itself is never shown again, only its name and role.
            me?.let {
                val role = stringResource(if (it.role == Role.READ) R.string.role_read else R.string.role_full)
                Text(stringResource(R.string.settings_token_role, it.name, role))
            } ?: Text("••••••••")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_live), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.settings_live_text), style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = live,
                    onCheckedChange = { on -> if (on) notifications { setLive(true) } else setLive(false) },
                    modifier = Modifier.testTag("live-switch"),
                )
            }
            OutlinedButton(onClick = { config?.let { onReplace(it.url) } }) { Text(stringResource(R.string.settings_replace)) }
            OutlinedButton(onClick = { confirm = true }) { Text(stringResource(R.string.settings_forget)) }
            TextButton(onClick = onLicences) { Text(stringResource(R.string.settings_licences)) }
            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.settings_forget)) },
            text = { Text(stringResource(R.string.settings_forget_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch {
                        context.repository.forget()
                        redrawWidgets(context)
                        onForgotten()
                    }
                }) { Text(stringResource(R.string.settings_forget)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}
