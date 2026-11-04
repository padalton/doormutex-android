package net.padalton.doormutex.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.padalton.doormutex.android.live.LiveService
import net.padalton.doormutex.android.ui.overview.OverviewScreen
import net.padalton.doormutex.android.ui.pairing.PairingScreen
import net.padalton.doormutex.android.ui.settings.SettingsScreen
import net.padalton.doormutex.android.ui.theme.DoormutexTheme

class MainActivity : ComponentActivity() {
    /** Profile to scroll to, set when a widget opened the app. */
    private var focusProfile by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        focusProfile = intent.getStringExtra(EXTRA_PROFILE)
        setContent { DoormutexTheme { App(focusProfile) } }
    }

    override fun onStart() {
        super.onStart()
        // The app in the foreground is one of the few moments a foreground service may start.
        lifecycleScope.launch { LiveService.sync(this@MainActivity) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        focusProfile = intent.getStringExtra(EXTRA_PROFILE)
    }

    companion object {
        const val EXTRA_PROFILE = "profile"

        fun intent(
            context: Context,
            profile: String?,
        ) = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_PROFILE, profile)
    }
}

private enum class Start { PAIR, OVERVIEW }

@Composable
private fun App(focusProfile: String?) {
    val context = LocalContext.current
    // Decided once at launch; later changes navigate explicitly.
    var start by remember { mutableStateOf<Start?>(null) }
    LaunchedEffect(Unit) {
        start = if (context.repository.config.first() == null) Start.PAIR else Start.OVERVIEW
    }
    val initial = start ?: return

    val nav = rememberNavController()
    NavHost(nav, startDestination = initial.name) {
        composable(Start.PAIR.name) {
            PairingScreen(onPaired = {
                nav.navigate(Start.OVERVIEW.name) { popUpTo(0) }
            })
        }
        composable(Start.OVERVIEW.name) {
            OverviewScreen(focusProfile = focusProfile, onSettings = { nav.navigate("settings") })
        }
        composable("settings") {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onForgotten = { nav.navigate(Start.PAIR.name) { popUpTo(0) } },
            )
        }
    }
}
