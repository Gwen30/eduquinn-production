package zw.co.eduquinn.app

import android.app.Application
import zw.co.eduquinn.app.data.local.PersistentCookieJar
import zw.co.eduquinn.app.data.local.SecurePrefsStore
import zw.co.eduquinn.app.data.network.ApiService
import zw.co.eduquinn.app.data.network.NetworkModule
import zw.co.eduquinn.app.data.repository.AuthRepository
import zw.co.eduquinn.app.data.repository.CertificateRepository
import zw.co.eduquinn.app.data.repository.CommerceRepository
import zw.co.eduquinn.app.data.repository.CourseRepository
import zw.co.eduquinn.app.data.repository.LearningRepository
import zw.co.eduquinn.app.data.repository.LiveClassRepository
import zw.co.eduquinn.app.data.repository.MessagingRepository
import zw.co.eduquinn.app.data.repository.SupportRepository
import zw.co.eduquinn.app.data.repository.WishlistRepository

/**
 * No DI framework for this pass — a single ServiceLocator is enough given
 * the scope, and avoids adding Hilt/Koin on top of Kotlin+Compose+Retrofit
 * all landing in the same change. Every repository is a cheap, stateless
 * wrapper around the shared ApiService, so constructing them eagerly here
 * is fine.
 */
class EduQuinnApplication : Application() {

    lateinit var services: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        services = ServiceLocator(this)
    }
}

class ServiceLocator(context: Application) {
    private val securePrefsStore = SecurePrefsStore(context)
    val cookieJar = PersistentCookieJar(securePrefsStore)

    // Not private: ExoPlayer's OkHttpDataSource.Factory and the resource-lesson
    // "Open resource" action both need the SAME authenticated client (so the
    // eq_session cookie rides along to /api/media/:id), rather than Retrofit's
    // wrapped interface.
    val okHttpClient = NetworkModule.buildOkHttpClientWithJar(cookieJar)
    private val retrofit = NetworkModule.buildRetrofit(okHttpClient)
    val api: ApiService = NetworkModule.buildApiService(retrofit)

    val authRepository = AuthRepository(api, cookieJar)
    val courseRepository = CourseRepository(api)
    val wishlistRepository = WishlistRepository(api)
    val commerceRepository = CommerceRepository(api)
    val learningRepository = LearningRepository(api)
    val certificateRepository = CertificateRepository(api)
    val supportRepository = SupportRepository(api)
    val liveClassRepository = LiveClassRepository(api)
    val messagingRepository = MessagingRepository(api)
}
