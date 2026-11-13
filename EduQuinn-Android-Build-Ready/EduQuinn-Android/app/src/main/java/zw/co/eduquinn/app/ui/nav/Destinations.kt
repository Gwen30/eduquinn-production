package zw.co.eduquinn.app.ui.nav

/**
 * Route strings for Navigation-Compose. The 3 TOP_LEVEL routes are the
 * bottom-tab destinations (browsable logged-out, matching how the web app
 * itself behaves — only My Learning and Checkout actually require a
 * session). Everything else is a drill-in destination pushed on top, with
 * the bottom bar hidden.
 */
object Destinations {
    const val CATALOG = "catalog"
    const val MY_LEARNING = "my_learning"
    const val ACCOUNT = "account"

    const val LOGIN = "login?returnTo={returnTo}"
    const val LOGIN_BASE = "login"
    const val REGISTER = "register"
    const val COURSE_DETAIL = "course_detail/{courseId}"
    const val CHECKOUT = "checkout/{courseIdsCsv}"
    const val ORDER_STATUS = "order_status/{orderId}"
    const val LESSON_PLAYER = "lesson_player/{courseId}/{lessonId}"
    const val CERTIFICATE = "certificate/{courseId}"

    val TOP_LEVEL = setOf(CATALOG, MY_LEARNING, ACCOUNT)

    fun courseDetail(courseId: String) = "course_detail/$courseId"
    fun checkout(courseIdsCsv: String) = "checkout/$courseIdsCsv"
    fun orderStatus(orderId: String) = "order_status/$orderId"
    fun lessonPlayer(courseId: String, lessonId: String) = "lesson_player/$courseId/$lessonId"
    fun certificate(courseId: String) = "certificate/$courseId"
    fun login(returnTo: String? = null) =
        if (returnTo != null) "$LOGIN_BASE?returnTo=${java.net.URLEncoder.encode(returnTo, "UTF-8")}" else LOGIN_BASE
}
