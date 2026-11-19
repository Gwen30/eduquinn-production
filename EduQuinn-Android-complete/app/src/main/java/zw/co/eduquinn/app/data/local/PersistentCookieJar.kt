package zw.co.eduquinn.app.data.local

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * Persists cookies (specifically `eq_session`) across app restarts.
 *
 * The backend's entire auth model is a single HttpOnly session cookie set
 * by /api/auth/login and /api/auth/register — there is no bearer-token
 * scheme anywhere in this API, so a plain in-memory CookieJar (OkHttp's
 * default behavior) would silently log the user out every time the
 * process dies. HttpOnly only restricts JavaScript's document.cookie; it
 * has no effect on what an HTTP client like OkHttp can read or send, so
 * this works exactly like a browser's cookie jar would.
 */
class PersistentCookieJar(private val store: SecurePrefsStore) : CookieJar {

    @Serializable
    private data class StoredCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val expiresAt: Long,
        val secure: Boolean,
        val hostOnly: Boolean,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val cookies = ConcurrentHashMap<String, Cookie>()

    init {
        loadFromDisk()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        for (cookie in cookies) {
            val key = key(cookie.domain, cookie.name)
            if (cookie.expiresAt <= now) {
                this.cookies.remove(key)
            } else {
                this.cookies[key] = cookie
            }
        }
        persistToDisk()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val expired = mutableListOf<String>()
        val matching = cookies.entries.filter { (key, cookie) ->
            if (cookie.expiresAt <= now) {
                expired.add(key)
                false
            } else {
                cookie.matches(url)
            }
        }.map { it.value }
        if (expired.isNotEmpty()) {
            expired.forEach { cookies.remove(it) }
            persistToDisk()
        }
        return matching
    }

    /** Called from AuthRepository.logout() so a stale eq_session can't linger. */
    fun clear() {
        cookies.clear()
        store.putString(PREF_KEY, null)
    }

    private fun key(domain: String, name: String) = "$domain|$name"

    private fun loadFromDisk() {
        val raw = store.getString(PREF_KEY) ?: return
        try {
            val stored: List<StoredCookie> = json.decodeFromString(raw)
            val now = System.currentTimeMillis()
            for (sc in stored) {
                if (sc.expiresAt <= now) continue
                val cookie = Cookie.Builder()
                    .name(sc.name)
                    .value(sc.value)
                    .expiresAt(sc.expiresAt)
                    .let { if (sc.hostOnly) it.hostOnlyDomain(sc.domain) else it.domain(sc.domain) }
                    .path(sc.path)
                    .let { if (sc.secure) it.secure() else it }
                    .build()
                cookies[key(sc.domain, sc.name)] = cookie
            }
        } catch (_: Exception) {
            // Corrupt or unreadable store: start with an empty jar rather than crashing.
            store.putString(PREF_KEY, null)
        }
    }

    private fun persistToDisk() {
        val stored = cookies.values.map {
            StoredCookie(
                name = it.name,
                value = it.value,
                domain = it.domain,
                path = it.path,
                expiresAt = it.expiresAt,
                secure = it.secure,
                hostOnly = it.hostOnly,
            )
        }
        store.putString(PREF_KEY, json.encodeToString(stored))
    }

    private companion object {
        const val PREF_KEY = "cookies"
    }
}
