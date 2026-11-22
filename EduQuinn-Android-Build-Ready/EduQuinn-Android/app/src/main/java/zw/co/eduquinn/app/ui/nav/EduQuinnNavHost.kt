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
import zw.co.eduquinn.app.ui.screens.business.BusinessScreen
import zw.co.eduquinn.app.ui.screens.catalog.CatalogScreen
import zw.co.eduquinn.app.ui.screens.certificate.CertificateScreen
import zw.co.eduquinn.app.ui.screens.checkout.CheckoutScreen
import zw.co.eduquinn.app.ui.screens.checkout.OrderStatusScreen
import zw.co.eduquinn.app.ui.screens.coursedetail.CourseDetailScreen
import zw.co.eduquinn.app.ui.screens.instructor.InstructorStudioScreen
import zw.co.eduquinn.app.ui.screens.liveclasses.LiveClassRoomScreen
import zw.co.eduquinn.app.ui.screens.liveclasses.LiveClassesScreen
import zw.co.eduquinn.app.ui.screens.messages.ConversationScreen
import zw.co.eduquinn.app.ui.screens.messages.MessagesScreen
import zw.co.eduquinn.app.ui.screens.more.MoreScreen
import zw.co.eduquinn.app.ui.screens.mylearning.MyLearningScreen
import zw.co.eduquinn.app.ui.screens.player.LessonPlayerScreen
import zw.co.eduquinn.app.ui.screens.purchases.PurchasesScreen
import zw.co.eduquinn.app.ui.screens.support.PasswordHelpScreen
import zw.co.eduquinn.app.ui.screens.support.SupportScreen
import zw.co.eduquinn.app.ui.screens.wishlist.WishlistScreen

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
            composable(Destinations.MORE) { MoreScreen(navController = navController) }
            composable(Destinations.WISHLIST) { WishlistScreen(app = app, navController = navController) }

            composable(Destinations.BUSINESS) { BusinessScreen(navController = navController) }
            composable(Destinations.PASSWORD_HELP) { PasswordHelpScreen(navController = navController) }
            composable(Destinations.SUPPORT) { SupportScreen(app = app, navController = navController) }
            composable(Destinations.PURCHASES) { PurchasesScreen(app = app, navController = navController) }
            composable(Destinations.LIVE_CLASSES) { LiveClassesScreen(app = app, navController = navController) }
            composable(
                Destinations.LIVE_CLASS_ROOM,
                arguments = listOf(navArgument("classId") { type = NavType.StringType }),
            ) { entry ->
                LiveClassRoomScreen(
                    app = app,
                    navController = navController,
                    classId = entry.arguments?.getString("classId").orEmpty(),
                )
            }
            composable(Destinations.MESSAGES) { MessagesScreen(app = app, navController = navController) }
            composable(Destinations.INSTRUCTOR_STUDIO) { InstructorStudioScreen(app = app, navController = navController) }
            composable(
                Destinations.CONVERSATION,
                arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
            ) { entry ->
                ConversationScreen(
                    app = app,
                    navController = navController,
                    conversationId = entry.arguments?.getString("conversationId").orEmpty(),
                )
            }

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
                    onNavigateToRegister = { navController.navigate(Destinations.register()) },
                )
            }
            composable(
                Destinations.REGISTER,
                arguments = listOf(navArgument("role") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                RegisterScreen(
                    app = app,
                    navController = navController,
                    initialRole = entry.arguments?.getString("role"),
                )
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
