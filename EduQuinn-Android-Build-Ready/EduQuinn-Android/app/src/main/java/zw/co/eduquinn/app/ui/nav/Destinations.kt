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
    const val MORE = "more"

    const val LOGIN = "login?returnTo={returnTo}"
    const val LOGIN_BASE = "login"
    const val REGISTER = "register?role={role}"
    const val REGISTER_BASE = "register"
    const val COURSE_DETAIL = "course_detail/{courseId}"
    const val CHECKOUT = "checkout/{courseIdsCsv}"
    const val ORDER_STATUS = "order_status/{orderId}"
    const val LESSON_PLAYER = "lesson_player/{courseId}/{lessonId}"
    const val CERTIFICATE = "certificate/{courseId}"
    const val WISHLIST = "wishlist"

    const val BUSINESS = "business"
    const val PASSWORD_HELP = "password_help"
    const val SUPPORT = "support"
    const val PURCHASES = "purchases"
    const val LIVE_CLASSES = "live_classes"
    const val LIVE_CLASS_ROOM = "live_class_room/{classId}"
    const val MESSAGES = "messages"
    const val CONVERSATION = "conversation/{conversationId}"
    const val INSTRUCTOR_STUDIO = "instructor_studio"

    val TOP_LEVEL = setOf(CATALOG, MY_LEARNING, ACCOUNT, MORE)

    fun courseDetail(courseId: String) = "course_detail/$courseId"
    fun checkout(courseIdsCsv: String) = "checkout/$courseIdsCsv"
    fun orderStatus(orderId: String) = "order_status/$orderId"
    fun lessonPlayer(courseId: String, lessonId: String) = "lesson_player/$courseId/$lessonId"
    fun certificate(courseId: String) = "certificate/$courseId"
    fun liveClassRoom(classId: String) = "live_class_room/$classId"
    fun conversation(conversationId: String) = "conversation/$conversationId"
    fun login(returnTo: String? = null) =
        if (returnTo != null) "$LOGIN_BASE?returnTo=${java.net.URLEncoder.encode(returnTo, "UTF-8")}" else LOGIN_BASE
    fun register(role: String? = null) =
        if (role != null) "$REGISTER_BASE?role=${java.net.URLEncoder.encode(role, "UTF-8")}" else REGISTER_BASE
}
