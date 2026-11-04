package net.padalton.doormutex.android.ui.overview

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.padalton.doormutex.android.R
import net.padalton.doormutex.android.app
import net.padalton.doormutex.android.data.Freshness
import net.padalton.doormutex.android.data.LIVE_INTERVAL
import net.padalton.doormutex.android.data.Problem
import net.padalton.doormutex.android.data.ProfileInfo
import net.padalton.doormutex.android.data.Snapshot
import net.padalton.doormutex.android.live.LiveService
import net.padalton.doormutex.android.repository
import net.padalton.doormutex.android.ui.LocalNetworkHint
import net.padalton.doormutex.android.ui.TrafficLight
import net.padalton.doormutex.android.ui.describe
import net.padalton.doormutex.android.ui.nextFreeText
import net.padalton.doormutex.android.ui.reasonText
import net.padalton.doormutex.android.ui.rememberLocalNetworkAccess
import net.padalton.doormutex.android.ui.rememberNotificationRequest
import net.padalton.doormutex.android.ui.statusText
import net.padalton.doormutex.android.ui.time
import net.padalton.doormutex.android.ui.untilText
import net.padalton.doormutex.android.widget.refreshAndRedraw
import kotlin.time.Duration.Companion.seconds

private val TICK = 5.seconds

class OverviewViewModel(
    app: Application,
) : AndroidViewModel(app) {
    val snapshot =
        app.repository.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Snapshot())

    var refreshing by mutableStateOf(false)
        private set

    fun refresh() {
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            try {
                refreshAndRedraw(getApplication())
            } finally {
                refreshing = false
            }
        }
    }

    /** Live updates while the screen is visible (shared with the live service, see [net.padalton.doormutex.android.DoormutexApp.live]). */
    suspend fun live(): Nothing = getApplication<Application>().app.live.collect {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    onOpen: (profile: String) -> Unit,
    onSettings: () -> Unit,
    vm: OverviewViewModel = viewModel(),
) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Live while visible; re-evaluate staleness every few seconds.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { vm.live() }
            while (true) {
                now = System.currentTimeMillis()
                delay(TICK)
            }
        }
    }

    // Live mode is on by default; ask once for its notification (pairing asks too, see PairingScreen).
    val context = LocalContext.current
    val notifications = rememberNotificationRequest()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val repository = context.repository
        if (repository.liveMode.first() && !repository.notificationsAsked.first()) {
            repository.markNotificationsAsked()
            notifications { scope.launch { LiveService.sync(context) } }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = vm.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            val freshness = Freshness.of(snapshot, now, LIVE_INTERVAL)
            val localNetwork = rememberLocalNetworkAccess()
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("overview"),
                contentPadding =
                    androidx.compose.foundation.layout
                        .PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { LocalNetworkHint(localNetwork, onGranted = vm::refresh) }
                item { StaleBanner(snapshot, freshness) }
                if (snapshot.profiles.isEmpty() && snapshot.fetchedAt != 0L) {
                    item { Text(stringResource(R.string.no_profiles)) }
                }
                items(snapshot.profiles, key = { it.id }) { ProfileCard(it, freshness.stale) { onOpen(it.id) } }
            }
        }
    }
}

@Composable
private fun StaleBanner(
    snapshot: Snapshot,
    freshness: Freshness,
) {
    if (!freshness.stale) return
    val context = LocalContext.current
    val lines =
        listOfNotNull(
            when (snapshot.problem) {
                Problem.UNREACHABLE -> stringResource(R.string.hub_unreachable)
                Problem.UNAUTHORIZED -> stringResource(R.string.token_rejected)
                else -> null
            },
            freshness.since?.let { stringResource(R.string.no_update_since, context.time(it)) },
        )
    if (lines.isEmpty()) return
    Text(
        lines.joinToString(" · "),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag("stale"),
    )
}

@Composable
internal fun ProfileCard(
    profile: ProfileInfo,
    stale: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val card = Modifier.fillMaxWidth().testTag("profile-${profile.id}")
    if (onClick != null) {
        Card(onClick = onClick, modifier = card) { ProfileCardContent(profile, stale) }
    } else {
        Card(card) { ProfileCardContent(profile, stale) }
    }
}

@Composable
private fun ProfileCardContent(
    profile: ProfileInfo,
    stale: Boolean,
) {
    val context = LocalContext.current
    val state = profile.state
    val muted = if (stale) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .padding(16.dp)
            .clearAndSetSemantics { contentDescription = context.describe(profile) },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrafficLight(state.light, dimmed = stale)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(profile.name, style = MaterialTheme.typography.labelLarge, color = muted)
            Text(context.statusText(state), style = MaterialTheme.typography.titleLarge, color = muted)
            context.reasonText(state)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = muted) }
            Text(context.untilText(state), style = MaterialTheme.typography.bodyMedium, color = muted)
            context.nextFreeText(state)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = muted) }
            state.message?.let { Text("“$it”", style = MaterialTheme.typography.bodyLarge, color = muted) }
        }
    }
}
