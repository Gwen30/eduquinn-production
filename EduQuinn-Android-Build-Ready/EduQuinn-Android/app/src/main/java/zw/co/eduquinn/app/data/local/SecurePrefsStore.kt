package zw.co.eduquinn.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val PREFS_FILE_NAME = "eq_secure_prefs"

/**
 * Thin wrapper around EncryptedSharedPreferences. This is where the
 * `eq_session` cookie (via [PersistentCookieJar]) lives on disk — there is
 * no bearer-token fallback in this backend, so losing or leaking this
 * value is equivalent to losing or leaking the user's login session.
 *
 * This is constructed eagerly in [zw.co.eduquinn.app.ServiceLocator], which
 * runs from [zw.co.eduquinn.app.EduQuinnApplication.onCreate] — i.e. before
 * any activity or screen exists. EncryptedSharedPreferences.create() throws
 * if the on-disk prefs file can't be decrypted with the device's CURRENT
 * Android Keystore key. That mismatch happens whenever the file survives a
 * reinstall but the Keystore key doesn't — the manifest had
 * android:allowBackup="true" with no exclusions, so Android could restore
 * this exact file on a fresh install while the hardware-backed key (which
 * is never included in a backup) came back empty. With nothing catching
 * that exception, it crashed the whole process at launch, before the
 * splash screen's content could even draw — which matches a real device
 * that has gone through several version installs crashing while a clean
 * emulator install does not. See AndroidManifest.xml / data_extraction_rules.xml
 * for the other half of this fix, which stops the file from being backed
 * up going forward.
 */
class SecurePrefsStore(context: Context) {
    private val prefs: SharedPreferences = createPrefs(context)

    private fun createPrefs(context: Context): SharedPreferences = try {
        buildEncryptedPrefs(context)
    } catch (e: Exception) {
        // The existing file can't be decrypted with today's Keystore key
        // (restored-backup mismatch, or a corrupted entry). There's no
        // bearer-token fallback to recover from here, so the only sane
        // move is to drop the stale file and start clean — the user has
        // to log in again, but the app opens instead of crashing.
        context.deleteSharedPreferences(PREFS_FILE_NAME)
        buildEncryptedPrefs(context)
    }

    private fun buildEncryptedPrefs(context: Context): SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
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
