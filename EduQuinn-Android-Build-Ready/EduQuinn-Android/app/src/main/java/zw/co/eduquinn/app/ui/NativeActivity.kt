package zw.co.eduquinn.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.MainActivity
import zw.co.eduquinn.app.data.repository.SessionManager
import zw.co.eduquinn.app.ui.components.BrandSplashScreen
import zw.co.eduquinn.app.ui.nav.Destinations
import zw.co.eduquinn.app.ui.nav.EduQuinnNavHost
import zw.co.eduquinn.app.ui.theme.EduQuinnTheme

/**
 * The app's Compose entry point and, now that the native v1 core loop is
 * complete, the launcher/App-Links target (see AndroidManifest.xml) —
 * taking over both roles from MainActivity, which remains purely as the
 * WebView fallback for anything outside native scope.
 *
 * A deep link into a path this native app doesn't cover yet (live
 * classes, messaging, notifications, instructor/admin areas, purchase
 * history) is forwarded to MainActivity unchanged instead of crashing or
 * landing on a native 404 — see [resolveNativeStartRoute].
 */
class NativeActivity : ComponentActivity() {
    private var startRoute: String = Destinations.CATALOG

    override fun onCreate(savedInstanceState: Bundle?) {
        // The platform SplashScreen API (Theme.EduQuinn.Starting - cream
        // background + the EQ badge icon) can only show an icon on a flat
        // background, so it only needs to bridge the instant gap between
        // tapping the launcher icon and this Activity's first Compose
        // frame; it's released the moment that frame exists. The website's
        // full splash - badge + "EduQuinn" wordmark + tagline + loading
        // dots - is BrandSplashScreen, a Compose screen drawn right after,
        // which owns the actual ~3-5s branded moment below while the
        // session check runs (see the min/max timers in setContent).
        val splashScreen = installSplashScreen()
        var composeReady = false
        splashScreen.setKeepOnScreenCondition { !composeReady }

        super.onCreate(savedInstanceState)
        val app = application as EduQuinnApplication
        val incomingUri = intent?.data

        if (incomingUri != null) {
            val nativeRoute = resolveNativeStartRoute(incomingUri)
            if (nativeRoute == null) {
                // Not a path the native nav graph covers — hand it to the
                // existing WebView app unchanged, exactly as it already
                // handles arbitrary deep links today.
                startActivity(Intent(this, MainActivity::class.java).setData(incomingUri))
                finish()
                return
            }
            startRoute = nativeRoute
        }

        lifecycleScope.launch {
            app.services.authRepository.refreshSession()
        }

        setContent {
            composeReady = true
            // Forced light, not isSystemInDarkTheme()'s default: the
            // website has exactly one look (it has no dark mode), and the
            // app is meant to match it, not diverge into a near-black
            // Material scheme whenever the phone happens to be in system
            // dark mode.
            EduQuinnTheme(darkTheme = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val sessionInitialized by SessionManager.initialized.collectAsState()
                    var minTimeDone by remember { mutableStateOf(false) }
                    var forceDone by remember { mutableStateOf(false) }

                    // Same two bounds as before: never shorter than
                    // MIN_SPLASH_MS (so it reads as an intentional brand
                    // moment rather than a flicker on a fast connection),
                    // never longer than MAX_SPLASH_MS even if the session
                    // check stalls.
                    LaunchedEffect(Unit) {
                        delay(MIN_SPLASH_MS)
                        minTimeDone = true
                    }
                    LaunchedEffect(Unit) {
                        delay(MAX_SPLASH_MS)
                        forceDone = true
                    }

                    if ((sessionInitialized && minTimeDone) || forceDone) {
                        EduQuinnNavHost(app, startDestination = startRoute)
                    } else {
                        BrandSplashScreen()
                    }
                }
            }
        }
    }

    private companion object {
        const val MIN_SPLASH_MS = 3200L
        const val MAX_SPLASH_MS = 5000L
    }
}

/**
 * Only two paths are native-supported for this pass: the home/catalog
 * page, and a course detail page (`course-details.html?id=<uuid>`,
 * confirmed against the web app's own links — course-details.html always
 * takes the course's id as a plain query param, not a slug, so it maps
 * directly onto the native course_detail/{courseId} route with no extra
 * lookup). Every other eduquinn.co.zw path — live classes, messaging,
 * notifications, instructor/admin tools, checkout confirmation pages not
 * covered here, etc. — returns null so the caller forwards to MainActivity.
 */
internal fun resolveNativeStartRoute(uri: Uri): String? {
    val path = uri.path?.trimEnd('/').orEmpty()
    return when {
        path.isEmpty() || path == "/index.html" -> Destinations.CATALOG
        path == "/course-details.html" -> uri.getQueryParameter("id")?.let { Destinations.courseDetail(it) }
        else -> null
    }
}
