package net.padalton.doormutex.android.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import net.padalton.doormutex.android.R

/**
 * Android 17 (API 37) makes access to devices in the local network a runtime
 * permission. The hub is always in the home network, so without it the app
 * sees nothing: connections time out as if the hub were down.
 */
object LocalNetwork {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    private const val SINCE_API = 37

    fun needed() = Build.VERSION.SDK_INT >= SINCE_API

    fun granted(context: Context) = !needed() || ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun appSettings(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
}

class LocalNetworkAccess(
    val granted: Boolean,
    val denied: Boolean,
    val request: (onGranted: () -> Unit) -> Unit,
)

/** Current state of the permission, re-read on resume (it can be changed in the system settings). */
@Composable
fun rememberLocalNetworkAccess(): LocalNetworkAccess {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(LocalNetwork.granted(context)) }
    var denied by remember { mutableStateOf(false) }
    var then by remember { mutableStateOf<(() -> Unit)?>(null) }
    LifecycleResumeEffect(Unit) {
        granted = LocalNetwork.granted(context)
        onPauseOrDispose {}
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
            denied = !ok
            if (ok) then?.invoke()
            then = null
        }
    return LocalNetworkAccess(granted, denied) { onGranted ->
        if (LocalNetwork.granted(context)) {
            onGranted()
        } else {
            then = onGranted
            launcher.launch(LocalNetwork.PERMISSION)
        }
    }
}

/** Explains the permission; shown when it is missing. */
@Composable
fun LocalNetworkHint(
    access: LocalNetworkAccess,
    modifier: Modifier = Modifier,
    onGranted: () -> Unit = {},
) {
    if (access.granted) return
    val context = LocalContext.current
    Column(modifier.testTag("local-network"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.local_network_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(if (access.denied) R.string.local_network_denied else R.string.local_network_text),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (access.denied) {
            OutlinedButton(onClick = { context.startActivity(LocalNetwork.appSettings(context)) }) {
                Text(stringResource(R.string.local_network_settings))
            }
        } else {
            OutlinedButton(onClick = { access.request(onGranted) }) { Text(stringResource(R.string.local_network_grant)) }
        }
    }
}
