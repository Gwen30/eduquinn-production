package zw.co.eduquinn.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.MainActivity
import zw.co.eduquinn.app.data.repository.SessionManager
import zw.co.eduquinn.app.ui.components.LoadingState
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
    // Three independent latches, combined below: the branded splash stays
    // up until the session check finishes AND at least MIN_SPLASH_MS has
    // passed (so it reads as an intentional brand moment rather than a
    // flicker on a fast connection), but never longer than MAX_SPLASH_MS
    // even if the network call stalls — both bounds keep it inside the
    // ~3-5s window this was asked to land in.
    @Volatile private var sessionDone = false
    @Volatile private var minTimeDone = false
    @Volatile private var forceDone = false
    private var startRoute: String = Destinations.CATALOG

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be installed before super.onCreate(): shows the same branded
        // cream-background/badge splash MainActivity used to show on cold
        // start (Theme.EduQuinn.Starting in styles.xml), now bridging the
        // moment between tapping the icon and the first Compose frame
        // instead of the WebView's first frame.
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !((sessionDone && minTimeDone) || forceDone) }
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({ minTimeDone = true }, MIN_SPLASH_MS)
        handler.postDelayed({ forceDone = true }, MAX_SPLASH_MS)

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
            sessionDone = true
        }

        setContent {
            EduQuinnTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val initialized by SessionManager.initialized.collectAsState()
                    if (initialized) {
                        EduQuinnNavHost(app, startDestination = startRoute)
                    } else {
                        LoadingState()
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
