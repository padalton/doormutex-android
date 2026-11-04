package net.padalton.doormutex.android.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import net.padalton.doormutex.android.repository
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * Refreshes the cached state and redraws the widgets. Runs every
 * [INTERVAL] (the minimum WorkManager allows) while a network is up.
 */
class RefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        refreshAndRedraw(applicationContext)
        return Result.success()
    }

    companion object {
        val INTERVAL = 15.minutes
        private const val NAME = "refresh"

        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<RefreshWorker>(INTERVAL.toJavaDuration())
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** Fetches from the hub (if paired) and redraws every widget, also on failure: it may now be stale. */
suspend fun refreshAndRedraw(context: Context) {
    context.repository.refresh()
    redrawWidgets(context)
}

suspend fun redrawWidgets(context: Context) {
    SingleLightWidget().updateAll(context)
}
