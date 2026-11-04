package net.padalton.doormutex.android

import android.app.Application
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import net.padalton.doormutex.android.data.HubRepository
import net.padalton.doormutex.android.data.keepCurrent
import net.padalton.doormutex.android.widget.RefreshWorker
import net.padalton.doormutex.android.widget.redrawWidgets
import kotlin.time.Duration.Companion.minutes

class DoormutexApp : Application() {
    lateinit var repository: HubRepository
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * One connection to the hub for the whole process, open while anybody
     * collects it: the overview while visible, the live service while on the
     * home network. Every change redraws the widgets.
     */
    val live: SharedFlow<Unit> by lazy {
        flow<Unit> { keepCurrent(repository) { redrawWidgets(this@DoormutexApp) } }
            .retryWhen { cause, _ ->
                // Not a hub error (those are handled inside): log, wait, start over.
                Log.w("doormutex", "live updates failed", cause)
                delay(1.minutes)
                true
            }.shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000))
    }

    override fun onCreate() {
        super.onCreate()
        repository = HubRepository(this)
        RefreshWorker.schedule(this)
    }
}

val Context.app: DoormutexApp
    get() = applicationContext as DoormutexApp

val Context.repository: HubRepository
    get() = app.repository
