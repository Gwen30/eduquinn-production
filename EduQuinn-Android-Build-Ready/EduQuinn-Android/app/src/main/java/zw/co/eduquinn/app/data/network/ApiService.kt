package zw.co.eduquinn.app.data.network

import zw.co.eduquinn.app.data.model.*
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * One-to-one with the routes in api/index.js that the v1 native screens
 * need (auth, courses, wishlist, commerce/orders, enrolments/learning,
 * certificates). Every endpoint here is cookie-session authenticated —
 * see NetworkModule's OkHttpClient / PersistentCookieJar for how the
 * `eq_session` cookie actually gets attached; nothing here adds headers
 * for auth. `GET /api/media/:assetId` is deliberately NOT declared here —
 * ExoPlayer hits it directly through an OkHttp-backed DataSource so it can
 * follow the 302 redirect and honor Range requests itself.
 */
interface ApiService {

    // --- Auth ---
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @GET("api/auth/session")
    suspend fun session(): SessionResponse

    @POST("api/auth/logout")
    suspend fun logout()

    @POST("api/auth/mfa/verify")
    suspend fun verifyMfa(@Body body: MfaVerifyRequest): AuthResponse

    @POST("api/auth/resend-verification")
    suspend fun resendVerification(@Body body: ResendVerificationRequest)

    // --- Catalog ---
    @GET("api/courses")
    suspend fun courses(@Query("level") level: String? = null): CoursesResponse

    @GET("api/public/stats")
    suspend fun publicStats(): PublicStats

    @GET("api/courses/{id}")
    suspend fun courseDetail(@Path("id") id: String): CourseDetailResponse

    // --- Wishlist ---
    @POST("api/wishlist/{id}")
    suspend fun addWishlist(@Path("id") id: String)

    @DELETE("api/wishlist/{id}")
    suspend fun removeWishlist(@Path("id") id: String)

    @GET("api/wishlist")
    suspend fun wishlist(): WishlistResponse

    // --- Commerce / checkout ---
    @GET("api/commerce/config")
    suspend fun commerceConfig(): CommerceConfig

    @POST("api/coupons/validate")
    suspend fun validateCoupon(@Body body: CouponRequest): CouponResponse

    @POST("api/orders")
    suspend fun createOrder(@Body body: OrderCreateRequest): OrderCreateResponse

    @POST("api/orders/{id}/refresh-status")
    suspend fun refreshOrderStatus(@Path("id") id: String): OrderResponse

    @POST("api/orders/{id}/pay/paynow")
    suspend fun retryPaynow(@Path("id") id: String): PaynowRedirectResponse

    @GET("api/orders/mine")
    suspend fun ordersMine(): OrdersMineResponse

    @POST("api/orders/{id}/refund-request")
    suspend fun requestRefund(@Path("id") id: String, @Body body: RefundRequestBody): RefundRequestResponse

    // --- Learning ---
    @GET("api/enrolments")
    suspend fun enrolments(): EnrolmentsResponse

    @GET("api/learning/courses/{id}")
    suspend fun learningCourse(@Path("id") id: String): LearningLessonsResponse

    @GET("api/learning/courses/{id}/assessments")
    suspend fun assessments(@Path("id") id: String): AssessmentsResponse

    @POST("api/learning/assessments/{id}/submit")
    suspend fun submitAssessment(@Path("id") id: String, @Body body: AssessmentSubmitRequest): AssessmentResult

    @POST("api/learning/lessons/{id}/progress")
    suspend fun postProgress(@Path("id") id: String, @Body body: LessonProgressRequest): LessonProgressResponse

    // --- Certificate ---
    @POST("api/certificates/issue")
    suspend fun issueCertificate(@Body body: CertificateIssueRequest): CertificateResponse

    // --- Support ---
    @POST("api/support")
    suspend fun submitSupport(@Body body: SupportRequest): SupportResponse

    // --- Live classes (student-facing subset only — instructor scheduling
    // endpoints are deliberately not declared here, see MoreScreen.kt) ---
    @GET("api/live-classes")
    suspend fun liveClasses(): LiveClassesResponse

    @POST("api/live-classes/{id}/reserve")
    suspend fun reserveLiveClass(@Path("id") id: String): LiveClassReservedResponse

    @DELETE("api/live-classes/{id}/reserve")
    suspend fun cancelLiveClassReservation(@Path("id") id: String): LiveClassReservedResponse

    @POST("api/live-classes/{id}/join")
    suspend fun joinLiveClass(@Path("id") id: String): LiveClassJoinResponse

    @POST("api/live-classes/{id}/leave")
    suspend fun leaveLiveClass(@Path("id") id: String)

    @GET("api/live-classes/{id}/messages")
    suspend fun liveClassMessages(@Path("id") id: String): LiveClassMessagesResponse

    @POST("api/live-classes/{id}/messages")
    suspend fun postLiveClassMessage(@Path("id") id: String, @Body body: LiveClassMessageRequest)

    // --- Messages (student side of the shared conversations/messages API) ---
    @GET("api/conversations")
    suspend fun conversations(): ConversationsResponse

    @POST("api/conversations")
    suspend fun createConversation(@Body body: ConversationCreateRequest): ConversationCreateResponse

    @GET("api/conversations/{id}/messages")
    suspend fun conversationMessages(@Path("id") id: String): ConversationMessagesResponse

    @POST("api/conversations/{id}/messages")
    suspend fun postConversationMessage(@Path("id") id: String, @Body body: ConversationMessageRequest): ConversationMessageSentResponse
}
