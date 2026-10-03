package com.dev.alex.portfolio

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.dev.alex.portfolio.data.api.InvalidCredentialsException
import com.dev.alex.portfolio.data.api.PortfolioDto
import com.dev.alex.portfolio.data.api.SessionManager
import com.dev.alex.portfolio.data.api.describeError
import com.dev.alex.portfolio.data.api.isOfflineError
import com.dev.alex.portfolio.data.auth.BiometricCancelledException
import com.dev.alex.portfolio.data.auth.CredentialsInvalidatedException
import com.dev.alex.portfolio.data.settings.ThemeMode
import com.dev.alex.portfolio.domain.PORTFOLIO_PALETTE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where the app is. Every portfolio page carries its portfolio. */
sealed interface Screen {
    val portfolioId: String? get() = null

    data object Portfolios : Screen
    data class Dashboard(override val portfolioId: String) : Screen
    data class Holdings(override val portfolioId: String) : Screen
    data class Transactions(override val portfolioId: String) : Screen
    data class Dividends(override val portfolioId: String) : Screen
    data class Calendar(override val portfolioId: String) : Screen
    data class SelfFunding(override val portfolioId: String) : Screen

    /** The add-transaction form. [openedAt] gives every opening a fresh form (its own view-model). */
    data class NewTransaction(override val portfolioId: String, val openedAt: Long = System.nanoTime()) : Screen
}

sealed interface AuthState {
    data object Starting : AuthState

    /** A fingerprint-protected sign-in is stored; one touch opens the app. */
    data class Locked(
        val username: String,
        val message: String? = null,
        val busy: Boolean = false,
    ) : AuthState

    data class SignIn(
        val server: String,
        val username: String,
        val biometricAvailable: Boolean,
        val message: String? = null,
        val busy: Boolean = false,
    ) : AuthState

    /** [offline]: the identity was proven by fingerprint but the server was out of reach. */
    data class Ready(val username: String, val offline: Boolean) : AuthState
}

/**
 * Sign-in, the fingerprint lock and navigation. Each launch starts locked: the stored
 * password opens only through BiometricPrompt, and it is what signs in — so even a cookie
 * left over from a previous process could not skip the fingerprint.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = (application as PortfolioApp).container

    private val mutableAuth = MutableStateFlow<AuthState>(AuthState.Starting)
    val auth: StateFlow<AuthState> = mutableAuth.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = app.settings.prefs
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.System)

    private val mutablePortfolios = MutableStateFlow<List<PortfolioDto>>(emptyList())
    val portfolios: StateFlow<List<PortfolioDto>> = mutablePortfolios.asStateFlow()

    /** portfolioId → avatar colour; follows the value order of the portfolio list once known */
    private val mutableColors = MutableStateFlow<Map<String, Long>>(emptyMap())
    val portfolioColors: StateFlow<Map<String, Long>> = mutableColors.asStateFlow()

    /** Back stack: the portfolio list at the root, at most one page above it, and a form over that. */
    val stack = mutableStateListOf<Screen>(Screen.Portfolios)

    /**
     * Screen view-models live in a store of their own, replaced on every sign-in and
     * sign-out — so one account's figures can never show under the next, while the store
     * still survives rotation (this view-model does).
     */
    var screenStore = ViewModelStore()
        private set

    private fun resetScreens() {
        screenStore.clear()
        screenStore = ViewModelStore()
    }

    override fun onCleared() {
        screenStore.clear()
    }

    init {
        viewModelScope.launch { app.sessionExpired.collect { onSessionExpired() } }
        viewModelScope.launch { mutableAuth.value = initialState() }
    }

    // ------------------------------------------------------------------ navigation

    fun open(screen: Screen) {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        if (screen != Screen.Portfolios) stack.add(screen)
    }

    /** A form over the current page; back returns to that page as it was. */
    fun push(screen: Screen) {
        stack.add(screen)
    }

    /** false = nothing left to go back to; the activity may close */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { app.settings.setThemeMode(mode) }
    }

    fun rememberPortfolioOrder(idsByValue: List<String>) {
        mutableColors.value = idsByValue.mapIndexed { index, id -> id to PORTFOLIO_PALETTE[index % PORTFOLIO_PALETTE.size] }.toMap()
        // the drawer's list may have failed at sign-in (offline, nothing cached yet)
        if (mutablePortfolios.value.isEmpty()) loadPortfolios()
    }

    fun colorOf(portfolioId: String): Long {
        mutableColors.value[portfolioId]?.let { return it }
        val index = mutablePortfolios.value.indexOfFirst { it.portfolioId == portfolioId }.coerceAtLeast(0)
        return PORTFOLIO_PALETTE[index % PORTFOLIO_PALETTE.size]
    }

    // ------------------------------------------------------------------ sign-in

    private suspend fun initialState(): AuthState {
        val stored = app.credentials.stored()
        return if (stored != null) AuthState.Locked(stored.username) else signInState()
    }

    private suspend fun signInState(message: String? = null): AuthState.SignIn {
        val prefs = app.settings.current()
        return AuthState.SignIn(
            server = prefs.serverUrl,
            username = prefs.lastUsername,
            biometricAvailable = app.biometrics.isAvailable(getApplication<Application>()),
            message = message,
        )
    }

    fun usePassword() {
        viewModelScope.launch { mutableAuth.value = signInState() }
    }

    /** Fingerprint → stored password → sign in. Called when the lock screen shows. */
    fun unlock() {
        val locked = mutableAuth.value as? AuthState.Locked ?: return
        if (locked.busy) return
        viewModelScope.launch {
            val stored = app.credentials.stored()
            if (stored == null) {
                mutableAuth.value = signInState()
                return@launch
            }
            mutableAuth.value = locked.copy(busy = true, message = null)
            val password = try {
                val cipher = app.credentials.decryptionCipher(stored)
                val unlocked = app.biometrics.authenticate(cipher, "Unlock FinancePortfolio", stored.username)
                app.credentials.decrypt(unlocked, stored)
            } catch (e: CancellationException) {
                throw e
            } catch (e: CredentialsInvalidatedException) {
                mutableAuth.value = signInState(e.message)
                return@launch
            } catch (e: BiometricCancelledException) {
                mutableAuth.value = if (e.userChosePassword) {
                    signInState()
                } else {
                    locked.copy(busy = false, message = if (e.userDismissed) null else e.message)
                }
                return@launch
            } catch (e: Exception) {
                // a blob this key can't open (restored data, keystore reset): start clean
                app.credentials.clear()
                mutableAuth.value = signInState("The saved sign-in could not be opened. Sign in with your password.")
                return@launch
            }
            completeUnlock(stored.serverUrl, stored.username, password)
        }
    }

    private suspend fun completeUnlock(server: String, username: String, password: String) {
        val url = try {
            SessionManager.parseServerUrl(server)
        } catch (e: Exception) {
            app.credentials.clear()
            mutableAuth.value = signInState(describeError(e))
            return
        }
        // set first: offline, later requests still sign in with these once the server is back
        app.session.begin(url, username, password)
        try {
            app.api.login(url, username, password)
            enterApp(username, offline = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: InvalidCredentialsException) {
            app.session.clear()
            app.credentials.clear()
            mutableAuth.value = signInState("The saved password no longer works. Sign in again.")
        } catch (e: Exception) {
            if (isOfflineError(e)) {
                enterApp(username, offline = true)
            } else {
                app.session.clear()
                mutableAuth.value = AuthState.Locked(username, message = describeError(e))
            }
        }
    }

    fun signIn(server: String, username: String, password: String, rememberWithFingerprint: Boolean) {
        val form = mutableAuth.value as? AuthState.SignIn ?: return
        if (form.busy) return
        viewModelScope.launch {
            mutableAuth.value = form.copy(server = server, username = username, busy = true, message = null)
            val user = username.trim()
            try {
                val url = SessionManager.parseServerUrl(server)
                app.api.login(url, user, password)
                app.session.begin(url, user, password)
                app.settings.rememberSignIn(url.toString(), user)
                if (rememberWithFingerprint && app.biometrics.isAvailable(getApplication<Application>())) {
                    rememberCredentials(url.toString(), user, password)
                }
                enterApp(user, offline = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableAuth.value = form.copy(server = server, username = username, busy = false, message = describeError(e))
            }
        }
    }

    /** Optional: a cancelled prompt just means the password is not remembered. */
    private suspend fun rememberCredentials(server: String, username: String, password: String) {
        try {
            val cipher = app.credentials.encryptionCipher()
            val unlocked = app.biometrics.authenticate(cipher, "Remember this sign-in", "Touch the sensor to unlock with your fingerprint next time")
            app.credentials.save(unlocked, server, username, password)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // not remembered; the user signs in with the password next time
        }
    }

    private fun enterApp(username: String, offline: Boolean) {
        resetScreens()
        stack.clear()
        stack.add(Screen.Portfolios)
        mutableAuth.value = AuthState.Ready(username, offline)
        loadPortfolios()
    }

    fun loadPortfolios() {
        viewModelScope.launch {
            runCatching { app.repository.portfolios() }.onSuccess { mutablePortfolios.value = it.value }
        }
    }

    /** Sign out forgets everything this phone holds: session, fingerprint sign-in, cache. */
    fun signOut() {
        viewModelScope.launch {
            app.api.logout()
            app.session.clear()
            app.cookies.clear()
            app.credentials.clear()
            app.repository.clearCache()
            mutablePortfolios.value = emptyList()
            mutableColors.value = emptyMap()
            mutableAuth.value = signInState()
            resetScreens()
        }
    }

    private suspend fun onSessionExpired() {
        if (mutableAuth.value !is AuthState.Ready) return
        app.session.clear()
        app.cookies.clear()
        val stored = app.credentials.stored()
        mutableAuth.value = if (stored != null) {
            AuthState.Locked(stored.username, message = "Your session ended. Unlock to continue.")
        } else {
            signInState("Your session ended. Sign in again.")
        }
    }
}
