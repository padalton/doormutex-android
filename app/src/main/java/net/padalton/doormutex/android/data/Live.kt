package net.padalton.doormutex.android.data

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * While live, the data is confirmed at least this often: by the stream's
 * keep-alive (every 25 s) or, on a hub without the stream (before API
 * 0.2.0), by polling at this interval. The stale check uses it.
 */
val LIVE_INTERVAL = 30.seconds

private val RETRY_MIN = 5.seconds
private val RETRY_MAX = 5.minutes

/**
 * Keeps the cached snapshot current for as long as it runs: one full fetch
 * per connection (names, new profiles), then the event stream. The hub ends
 * the stream every 10 minutes, so that is a reconnect, not an error. Errors
 * are recorded in the snapshot (the UI dims it) and retried with backoff;
 * a hub without the stream is polled every [LIVE_INTERVAL] instead.
 *
 * [changed] runs after every change of the snapshot, e.g. to redraw the widgets.
 * Restarts on its own when the hub is paired again or forgotten.
 */
suspend fun keepCurrent(
    repository: HubRepository,
    changed: suspend () -> Unit,
) {
    repository.config.collectLatest { config ->
        if (config == null) return@collectLatest
        var retry = RETRY_MIN
        var poll = false
        while (true) {
            if (poll) {
                repository.refresh()
                changed()
                delay(LIVE_INTERVAL)
                continue
            }
            val snapshot = repository.refresh()
            changed()
            if (snapshot?.problem != null) {
                delay(retry)
                retry = next(retry)
                continue
            }
            try {
                repository.events(config).collect { event ->
                    when (event) {
                        StreamEvent.KeepAlive -> {
                            repository.confirm()
                        }

                        is StreamEvent.Update -> {
                            if (!repository.apply(event.update)) repository.refresh()
                            changed()
                        }
                    }
                    retry = RETRY_MIN
                }
                // The hub closed the stream (every 10 minutes): reconnect right away.
            } catch (e: HubException) {
                if (e is HubException.Http && (e.code == 404 || e.code == 400)) {
                    Log.i("doormutex", "hub has no multi-profile stream (HTTP ${e.code}), polling")
                    poll = true
                    continue
                }
                repository.failed(e)
                changed()
                delay(retry)
                retry = next(retry)
            }
        }
    }
}

private fun next(d: Duration) = minOf(d * 2, RETRY_MAX)
