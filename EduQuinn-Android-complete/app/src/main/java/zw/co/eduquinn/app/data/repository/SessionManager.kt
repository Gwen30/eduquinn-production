package zw.co.eduquinn.app.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import zw.co.eduquinn.app.data.model.User

/**
 * Single source of truth for "who is logged in" across the whole app.
 * AuthRepository is the only thing that should call [set]; everything
 * else (nav guards, the Account tab, ViewModels) just observes [user].
 */
object SessionManager {
    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    // False until the initial GET /api/auth/session on cold start resolves,
    // so the nav host can show a brief loading state instead of flashing a
    // "logged out" UI for a user who actually has a valid persisted cookie.
    private val _initialized = MutableStateFlow(false)
    val initialized: StateFlow<Boolean> = _initialized.asStateFlow()

    fun set(user: User?) {
        _user.value = user
    }

    fun markInitialized() {
        _initialized.value = true
    }

    val isLoggedIn: Boolean get() = _user.value != null
}
