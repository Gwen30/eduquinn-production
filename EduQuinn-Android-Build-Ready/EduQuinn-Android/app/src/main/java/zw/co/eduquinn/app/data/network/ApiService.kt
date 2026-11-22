package zw.co.eduquinn.app.data.network

import zw.co.eduquinn.app.data.model.*
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
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

    // --- Live classes (student-facing subset — reserve/join/leave/chat;
    // instructor scheduling/attendance endpoints are declared separately
    // below under "Instructor Studio", since they're role-gated
    // differently on the backend and used by different screens) ---
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

    // --- Instructor Studio ---
    @GET("api/instructor/courses")
    suspend fun instructorCourses(): InstructorCoursesResponse

    @POST("api/instructor/courses")
    suspend fun createInstructorCourse(@Body body: CourseUpsertRequest): CourseUpsertResponse

    @PATCH("api/instructor/courses/{id}")
    suspend fun updateInstructorCourse(@Path("id") id: String, @Body body: CourseUpsertRequest): CourseUpsertResponse

    @DELETE("api/instructor/courses/{id}")
    suspend fun deleteInstructorCourse(@Path("id") id: String): CourseDeleteResponse

    @GET("api/instructor/courses/{id}/assets")
    suspend fun courseAssets(@Path("id") id: String): CourseAssetsResponse

    // No request body — this issues a signed upload ticket only. The
    // backend reads just the Content-Type header plus these query params;
    // the file bytes go to a separate raw PUT against the ticket's own
    // uploadUrl (see InstructorMediaRepository.uploadAsset).
    @PUT("api/instructor/courses/{id}/assets")
    suspend fun createUploadTicket(
        @Path("id") id: String,
        @Query("kind") kind: String,
        @Query("name") name: String,
    ): UploadTicket

    @POST("api/instructor/courses/{id}/assets/{assetId}/confirm")
    suspend fun confirmAsset(
        @Path("id") id: String,
        @Path("assetId") assetId: String,
        @Body body: ConfirmAssetRequest,
    ): ConfirmAssetResponse

    @GET("api/instructor/courses/{id}/lessons")
    suspend fun instructorLessons(@Path("id") id: String): InstructorLessonsResponse

    @POST("api/instructor/courses/{id}/lessons")
    suspend fun createLesson(@Path("id") id: String, @Body body: LessonCreateRequest): LessonCreateResponse

    @GET("api/instructor/earnings")
    suspend fun instructorEarnings(): InstructorEarningsResponse

    @GET("api/instructor/live-classes")
    suspend fun instructorLiveClasses(): InstructorLiveClassesResponse

    @POST("api/instructor/live-classes")
    suspend fun createInstructorLiveClass(@Body body: LiveClassCreateRequest): LiveClassCreateResponse

    @PATCH("api/instructor/live-classes/{id}")
    suspend fun updateInstructorLiveClass(@Path("id") id: String, @Body body: LiveClassUpdateRequest)

    @DELETE("api/instructor/live-classes/{id}")
    suspend fun cancelInstructorLiveClass(@Path("id") id: String)

    @GET("api/instructor/live-classes/{id}/attendance")
    suspend fun instructorLiveClassAttendance(@Path("id") id: String): AttendanceResponse

    @GET("api/announcements")
    suspend fun announcements(): AnnouncementsResponse

    @POST("api/announcements")
    suspend fun createAnnouncement(@Body body: AnnouncementCreateRequest): AnnouncementCreateResponse
}
