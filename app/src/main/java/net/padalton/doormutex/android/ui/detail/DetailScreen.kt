package net.padalton.doormutex.android.ui.detail

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.padalton.doormutex.android.R
import net.padalton.doormutex.android.app
import net.padalton.doormutex.android.data.Freshness
import net.padalton.doormutex.android.data.HubException
import net.padalton.doormutex.android.data.LIVE_INTERVAL
import net.padalton.doormutex.android.data.ProfileInfo
import net.padalton.doormutex.android.data.Role
import net.padalton.doormutex.android.data.Slot
import net.padalton.doormutex.android.data.Snapshot
import net.padalton.doormutex.android.data.Switch
import net.padalton.doormutex.android.data.Timeline
import net.padalton.doormutex.android.repository
import net.padalton.doormutex.android.ui.overview.ProfileCard
import net.padalton.doormutex.android.ui.reasonText
import net.padalton.doormutex.android.ui.statusText
import net.padalton.doormutex.android.ui.time
import net.padalton.doormutex.android.widget.redrawWidgets
import kotlin.time.Duration.Companion.seconds

class DetailViewModel(
    app: Application,
) : AndroidViewModel(app) {
    val snapshot = app.repository.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Snapshot())
    val me = app.repository.me.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var timeline by mutableStateOf<Timeline?>(null)
        private set
    var eink by mutableStateOf<ImageBitmap?>(null)
        private set

    /** The switch being sent, for a spinner and to block double taps. */
    var switching by mutableStateOf<Switch?>(null)
        private set

    /** Last switch error, already as text. */
    var error by mutableStateOf<String?>(null)
        private set

    /** Timeline and e-ink preview; both are optional extras, failures just hide them. */
    fun load(profile: String) {
        val repository = getApplication<Application>().repository
        viewModelScope.launch {
            val t = async { runCatching { repository.timeline(profile) }.getOrNull() }
            val e =
                async {
                    runCatching {
                        val png = repository.eink(profile)
                        BitmapFactory.decodeByteArray(png, 0, png.size)?.asImageBitmap()
                    }.getOrNull()
                }
            t.await()?.let { timeline = it }
            e.await()?.let { eink = it }
        }
    }

    fun switch(
        profile: ProfileInfo,
        switch: Switch,
    ) {
        if (switching != null) return
        switching = switch
        error = null
        val app = getApplication<Application>()
        viewModelScope.launch {
            try {
                app.repository.switch(profile, switch)
                redrawWidgets(app)
            } catch (e: HubException) {
                error =
                    when (e) {
                        is HubException.Unreachable -> app.getString(R.string.detail_unreachable)
                        is HubException.Forbidden -> app.getString(R.string.detail_read_only)
                        is HubException.Http -> app.getString(R.string.detail_failed, e.reason ?: "HTTP ${e.code}")
                        else -> app.getString(R.string.detail_failed, e.message)
                    }
            } finally {
                switching = null
            }
        }
    }

    suspend fun live(): Nothing = getApplication<Application>().app.live.collect {}
}

/** One profile: current state, switches (full tokens), the next 24 hours and the e-ink display. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    profileId: String,
    onBack: () -> Unit,
    vm: DetailViewModel = viewModel(),
) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val profile = snapshot.profiles.firstOrNull { it.id == profileId }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { vm.live() }
            while (true) {
                now = System.currentTimeMillis()
                delay(5.seconds)
            }
        }
    }
    // A new state usually means a new plan and a new e-ink picture.
    LaunchedEffect(profileId, profile?.state?.updated) { vm.load(profileId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profile?.name ?: profileId) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (profile == null) return@Scaffold
        val stale = Freshness.of(snapshot, now, LIVE_INTERVAL).stale
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("detail"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ProfileCard(profile, stale) }
            item { Switches(profile, readOnly = me?.role == Role.READ, vm) }
            item { Text(stringResource(R.string.detail_planned), style = MaterialTheme.typography.titleMedium) }
            val slots = vm.timeline?.slots.orEmpty()
            if (vm.timeline != null && slots.isEmpty()) item { Text(stringResource(R.string.detail_nothing_planned)) }
            items(slots) { SlotRow(it) }
            vm.eink?.let { bitmap ->
                item { Text(stringResource(R.string.detail_eink), style = MaterialTheme.typography.titleMedium) }
                item {
                    Image(
                        bitmap,
                        contentDescription = stringResource(R.string.detail_eink_description, profile.name),
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun Switches(
    profile: ProfileInfo,
    readOnly: Boolean,
    vm: DetailViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.detail_switch), style = MaterialTheme.typography.titleMedium)
        if (readOnly) {
            Text(stringResource(R.string.detail_read_only), style = MaterialTheme.typography.bodyMedium)
            return
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Switch.RED to R.string.switch_red,
                Switch.RED_HOUR to R.string.switch_red_hour,
                Switch.GREEN to R.string.switch_green,
                Switch.OFF to R.string.switch_off,
                Switch.AUTOMATIC to R.string.switch_automatic,
            ).forEach { (switch, label) ->
                FilledTonalButton(
                    onClick = { vm.switch(profile, switch) },
                    enabled = vm.switching == null,
                    modifier = Modifier.testTag("switch-${switch.name.lowercase()}"),
                ) {
                    if (vm.switching == switch) {
                        CircularProgressIndicator(Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                    }
                    Text(stringResource(label))
                }
            }
        }
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun SlotRow(slot: Slot) {
    val context = LocalContext.current
    val range =
        slot.until?.let { stringResource(R.string.detail_slot, context.time(slot.since), context.time(it)) }
            ?: stringResource(R.string.detail_slot_open, context.time(slot.since))
    Column {
        Text(range, style = MaterialTheme.typography.labelLarge)
        Text(
            listOfNotNull(context.statusText(slot.status), context.reasonText(slot.reason)).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
