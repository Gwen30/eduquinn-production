package zw.co.eduquinn.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Thin wrapper around EncryptedSharedPreferences. This is where the
 * `eq_session` cookie (via [PersistentCookieJar]) lives on disk — there is
 * no bearer-token fallback in this backend, so losing or leaking this
 * value is equivalent to losing or leaking the user's login session.
 */
class SecurePrefsStore(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "eq_secure_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun getString(key: String): String? = prefs.getString(key, null)

    fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
