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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.padalton.doormutex.android.data.PairLink
import net.padalton.doormutex.android.live.LiveService
import net.padalton.doormutex.android.ui.detail.DetailScreen
import net.padalton.doormutex.android.ui.overview.OverviewScreen
import net.padalton.doormutex.android.ui.pairing.PairingScreen
import net.padalton.doormutex.android.ui.settings.AboutScreen
import net.padalton.doormutex.android.ui.settings.LicencesScreen
import net.padalton.doormutex.android.ui.settings.SettingsScreen
import net.padalton.doormutex.android.ui.theme.DoormutexTheme

/** What an intent asks for: a profile (from a widget) or pairing (from a `doormutex://pair` link). */
sealed interface Request {
    data class Open(
        val profile: String,
    ) : Request

    data class Pair(
        val link: PairLink,
    ) : Request

    companion object {
        fun of(intent: Intent?): Request? {
            intent ?: return null
            intent.dataString?.let(PairLink::parse)?.let { return Pair(it) }
            return intent.getStringExtra(MainActivity.EXTRA_PROFILE)?.let(::Open)
        }
    }
}

class MainActivity : ComponentActivity() {
    private var request by mutableStateOf<Request?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Not again after a configuration change: it was handled already.
        if (savedInstanceState == null) request = Request.of(intent)
        setContent { DoormutexTheme { App(request) { request = null } } }
    }

    override fun onStart() {
        super.onStart()
        // The app in the foreground is one of the few moments a foreground service may start.
        lifecycleScope.launch { LiveService.sync(this@MainActivity) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        request = Request.of(intent)
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

private object Routes {
    const val PAIR = "pair"
    const val OVERVIEW = "overview"
    const val SETTINGS = "settings"
    const val LICENCES = "licences"
    const val ABOUT = "about"
    const val PROFILE = "profile/{id}"

    fun profile(id: String) = "profile/${android.net.Uri.encode(id)}"
}

@Composable
private fun App(
    request: Request?,
    onHandled: () -> Unit,
) {
    val context = LocalContext.current
    // Decided once at launch; later changes navigate explicitly.
    var start by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        start = if (context.repository.config.first() == null) Routes.PAIR else Routes.OVERVIEW
    }
    val initial = start ?: return

    val nav = rememberNavController()
    // Kept in memory only: a pairing link carries a token.
    var pairLink by remember { mutableStateOf<PairLink?>(null) }
    var replaceUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(request) {
        when (request) {
            is Request.Pair -> {
                pairLink = request.link
                nav.navigate(Routes.PAIR) { launchSingleTop = true }
            }

            is Request.Open -> {
                if (context.repository.config.first() != null) {
                    nav.navigate(Routes.profile(request.profile)) { popUpTo(Routes.OVERVIEW) }
                }
            }

            null -> {}
        }
        if (request != null) onHandled()
    }

    NavHost(nav, startDestination = initial) {
        composable(Routes.PAIR) {
            PairingScreen(
                onPaired = {
                    pairLink = null
                    replaceUrl = null
                    nav.navigate(Routes.OVERVIEW) { popUpTo(0) }
                },
                link = pairLink,
                initialUrl = replaceUrl,
                onBack =
                    if (nav.previousBackStackEntry != null) {
                        {
                            pairLink = null
                            replaceUrl = null
                            nav.popBackStack()
                        }
                    } else {
                        null
                    },
            )
        }
        composable(Routes.OVERVIEW) {
            OverviewScreen(onOpen = { nav.navigate(Routes.profile(it)) }, onSettings = { nav.navigate(Routes.SETTINGS) })
        }
        composable(Routes.PROFILE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            DetailScreen(profileId = entry.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onForgotten = { nav.navigate(Routes.PAIR) { popUpTo(0) } },
                onReplace = { url ->
                    replaceUrl = url
                    nav.navigate(Routes.PAIR)
                },
                onAbout = { nav.navigate(Routes.ABOUT) },
            )
        }
        composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }, onLicences = { nav.navigate(Routes.LICENCES) }) }
        composable(Routes.LICENCES) { LicencesScreen(onBack = { nav.popBackStack() }) }
    }
}
