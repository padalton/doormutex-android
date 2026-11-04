package net.padalton.doormutex.android.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.padalton.doormutex.android.MainActivity
import net.padalton.doormutex.android.R
import net.padalton.doormutex.android.app
import net.padalton.doormutex.android.data.Problem
import net.padalton.doormutex.android.repository

/**
 * Live mode for the widgets: keeps the hub's event stream open while the
 * phone is on a network where the hub can be (Wi-Fi, Ethernet, VPN) and
 * redraws the widgets on every change. On other networks it stays idle,
 * without any network traffic, so it can resume on its own when the phone
 * comes home – a foreground service cannot be started from the background.
 *
 * Foreground service type `specialUse`: `dataSync` is limited to 6 hours a
 * day since Android 15, and no other type fits a status display.
 */
class LiveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val homeNetwork = MutableStateFlow(false)
    private lateinit var connectivity: ConnectivityManager
    private var text = R.string.live_waiting

    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                caps: NetworkCapabilities,
            ) {
                homeNetwork.value = isHomeCapable(caps)
            }

            override fun onLost(network: Network) {
                homeNetwork.value = false
            }
        }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        connectivity = getSystemService(ConnectivityManager::class.java)
        connectivity.registerDefaultNetworkCallback(callback)

        // Stop when live mode is switched off or the hub is forgotten.
        scope.launch {
            repository.liveMode
                .combine(repository.config) { on, config -> on && config != null }
                .first { !it }
            stopSelf()
        }
        // Connect only on a network where the hub can be.
        scope.launch {
            homeNetwork.collectLatest { home ->
                if (home) app.live.collect {} // suspends until the network changes
            }
        }
        // Keep the notification honest.
        scope.launch {
            homeNetwork
                .combine(repository.snapshot.map { it.problem }) { home, problem ->
                    when {
                        !home -> R.string.live_waiting
                        problem == Problem.UNREACHABLE -> R.string.hub_unreachable
                        problem != null -> R.string.live_problem
                        else -> R.string.live_connected
                    }
                }.distinctUntilChanged()
                .collect {
                    text = it
                    post()
                }
        }
    }

    /** Also called by [sync] when already running, e.g. after the notification permission was granted. */
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        post()
        return START_STICKY
    }

    private fun post() = getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())

    override fun onDestroy() {
        connectivity.unregisterNetworkCallback(callback)
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(): Notification =
        NotificationCompat
            .Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.live_title))
            .setContentText(getString(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    MainActivity.intent(this, null),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            ).build()

    companion object {
        private const val CHANNEL = "live"
        private const val NOTIFICATION_ID = 1

        /** Wi-Fi, Ethernet or a VPN (e.g. WireGuard to home); never mobile data alone. */
        fun isHomeCapable(caps: NetworkCapabilities) =
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

        fun createChannel(context: Context) {
            val channel =
                NotificationChannel(CHANNEL, context.getString(R.string.live_channel), NotificationManager.IMPORTANCE_MIN).apply {
                    description = context.getString(R.string.live_channel_description)
                    setShowBadge(false)
                }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        /**
         * Starts or stops the service to match the settings. Only works while
         * the app is in the foreground or right after boot / an update.
         */
        suspend fun sync(context: Context) {
            val wanted = context.repository.liveMode.first() && context.repository.config.first() != null
            val intent = Intent(context, LiveService::class.java)
            if (!wanted) {
                context.stopService(intent)
                return
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException (API 31+): called from the background.
                Log.i("doormutex", "live service not started: ${e.message}")
            }
        }
    }
}

/** Resumes live mode after a reboot or an app update – the only background starts Android allows. */
class LiveStartReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                LiveService.sync(context)
            } finally {
                pending.finish()
            }
        }
    }
}
