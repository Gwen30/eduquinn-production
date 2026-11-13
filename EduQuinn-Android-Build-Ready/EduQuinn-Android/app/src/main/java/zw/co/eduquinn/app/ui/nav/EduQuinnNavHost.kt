package zw.co.eduquinn.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.ui.screens.account.AccountScreen
import zw.co.eduquinn.app.ui.screens.auth.LoginScreen
import zw.co.eduquinn.app.ui.screens.auth.RegisterScreen
import zw.co.eduquinn.app.ui.screens.catalog.CatalogScreen
import zw.co.eduquinn.app.ui.screens.certificate.CertificateScreen
import zw.co.eduquinn.app.ui.screens.checkout.CheckoutScreen
import zw.co.eduquinn.app.ui.screens.checkout.OrderStatusScreen
import zw.co.eduquinn.app.ui.screens.coursedetail.CourseDetailScreen
import zw.co.eduquinn.app.ui.screens.mylearning.MyLearningScreen
import zw.co.eduquinn.app.ui.screens.player.LessonPlayerScreen

/**
 * Top-level NavHost. Catalog/My Learning/Account are the 3 bottom-tab
 * destinations (public — browsable logged out, matching the web app's own
 * behavior); everything else is a drill-in destination with the bottom
 * bar hidden and its own login gate where the API requires a session
 * (My Learning, Checkout).
 */
@Composable
fun EduQuinnNavHost(
    app: EduQuinnApplication,
    startDestination: String = Destinations.CATALOG,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.hierarchy?.any { dest ->
        dest.route in Destinations.TOP_LEVEL
    } == true

    Scaffold(
        bottomBar = { if (showBottomBar) EduQuinnBottomNavBar(navController) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destinations.CATALOG) { CatalogScreen(app = app, navController = navController) }
            composable(Destinations.MY_LEARNING) { MyLearningScreen(app = app, navController = navController) }
            composable(Destinations.ACCOUNT) { AccountScreen(app = app, navController = navController) }

            composable(
                Destinations.COURSE_DETAIL,
                arguments = listOf(navArgument("courseId") { type = NavType.StringType }),
            ) { entry ->
                CourseDetailScreen(
                    app = app,
                    navController = navController,
                    courseId = entry.arguments?.getString("courseId").orEmpty(),
                )
            }

            composable(
                Destinations.LOGIN,
                arguments = listOf(navArgument("returnTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                LoginScreen(
                    app = app,
                    navController = navController,
                    returnTo = entry.arguments?.getString("returnTo"),
                    onNavigateToRegister = { navController.navigate(Destinations.REGISTER) },
                )
            }
            composable(Destinations.REGISTER) {
                RegisterScreen(app = app, navController = navController)
            }

            composable(
                Destinations.CHECKOUT,
                arguments = listOf(navArgument("courseIdsCsv") { type = NavType.StringType }),
            ) { entry ->
                val csv = entry.arguments?.getString("courseIdsCsv").orEmpty()
                CheckoutScreen(app = app, navController = navController, courseIds = csv.split(",").filter { it.isNotBlank() })
            }
            composable(
                Destinations.ORDER_STATUS,
                arguments = listOf(navArgument("orderId") { type = NavType.StringType }),
            ) { entry ->
                OrderStatusScreen(
                    app = app,
                    navController = navController,
                    orderId = entry.arguments?.getString("orderId").orEmpty(),
                )
            }
            composable(
                Destinations.LESSON_PLAYER,
                arguments = listOf(
                    navArgument("courseId") { type = NavType.StringType },
                    navArgument("lessonId") { type = NavType.StringType },
                ),
            ) { entry ->
                LessonPlayerScreen(
                    app = app,
                    navController = navController,
                    courseId = entry.arguments?.getString("courseId").orEmpty(),
                    initialLessonId = entry.arguments?.getString("lessonId").orEmpty(),
                )
            }
            composable(
                Destinations.CERTIFICATE,
                arguments = listOf(navArgument("courseId") { type = NavType.StringType }),
            ) { entry ->
                CertificateScreen(
                    app = app,
                    navController = navController,
                    courseId = entry.arguments?.getString("courseId").orEmpty(),
                )
            }
        }
    }
}
