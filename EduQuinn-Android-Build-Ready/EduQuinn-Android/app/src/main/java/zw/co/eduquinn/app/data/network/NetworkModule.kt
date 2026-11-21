package zw.co.eduquinn.app.data.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import zw.co.eduquinn.app.BuildConfig

/**
 * Builds the shared OkHttpClient/Retrofit/ApiService singletons. The
 * backend is entirely snake_case (price_cents, education_level, ...) while
 * every Kotlin model here is camelCase — JsonNamingStrategy.SnakeCase
 * handles that conversion globally so individual model fields don't need
 * @SerialName annotations.
 *
 * Deliberately no Origin header is ever added to requests. api/index.js's
 * sameOriginAllowed() only checks the Origin header on state-changing
 * requests and passes the request through whenever Origin is absent — a
 * plain OkHttp client never sends one, so this client needs no special
 * handling for it.
 */
object NetworkModule {

    const val BASE_URL = "https://eduquinn.co.zw/"

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    fun buildOkHttpClientWithJar(cookieJar: CookieJar): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(logging)
            .build()
    }

    fun buildRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    fun buildApiService(retrofit: Retrofit): ApiService = retrofit.create()
}
