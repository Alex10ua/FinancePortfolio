package com.dev.alex.portfolio

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dev.alex.portfolio.data.settings.ThemeMode
import com.dev.alex.portfolio.ui.auth.LockScreen
import com.dev.alex.portfolio.ui.auth.SignInScreen
import com.dev.alex.portfolio.ui.calendar.DividendCalendarScreen
import com.dev.alex.portfolio.ui.dashboard.DashboardScreen
import com.dev.alex.portfolio.ui.dividends.DividendsScreen
import com.dev.alex.portfolio.ui.holdings.HoldingsScreen
import com.dev.alex.portfolio.ui.portfolios.PortfolioListScreen
import com.dev.alex.portfolio.ui.selffunding.SelfFundingScreen
import com.dev.alex.portfolio.ui.shell.ShellNav
import com.dev.alex.portfolio.ui.theme.FinanceTheme
import com.dev.alex.portfolio.ui.theme.Fp
import com.dev.alex.portfolio.ui.transactions.NewTransactionScreen
import com.dev.alex.portfolio.ui.transactions.TransactionsScreen

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    SystemBarIcons(dark)

    FinanceTheme(dark = dark) {
        val auth by vm.auth.collectAsStateWithLifecycle()
        when (val state = auth) {
            AuthState.Starting -> Box(
                Modifier
                    .fillMaxSize()
                    .background(Fp.colors.pageBg),
            )
            is AuthState.Locked -> LockScreen(state, onUnlock = vm::unlock, onUsePassword = vm::usePassword)
            is AuthState.SignIn -> SignInScreen(state, onSubmit = vm::signIn)
            is AuthState.Ready -> Pages(vm, state, dark)
        }
    }
}

/** The theme can differ from the system's, so the status-bar icons follow the app. */
@Composable
private fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }
}

@Composable
private fun Pages(vm: AppViewModel, ready: AuthState.Ready, dark: Boolean) {
    val portfolios by vm.portfolios.collectAsStateWithLifecycle()
    // read so the drawer's avatar colours recompose when the list screen reorders them
    val colors by vm.portfolioColors.collectAsStateWithLifecycle()
    val screen = vm.stack.last()

    BackHandler(enabled = vm.stack.size > 1) { vm.back() }

    val nav = ShellNav(
        current = screen,
        portfolios = portfolios,
        username = ready.username,
        offline = ready.offline,
        dark = dark,
        colorOf = { id -> colors[id] ?: vm.colorOf(id) },
        open = vm::open,
        push = vm::push,
        back = { vm.back() },
        toggleTheme ={ vm.setThemeMode(if (dark) ThemeMode.Light else ThemeMode.Dark) },
        signOut = vm::signOut,
        rememberPortfolioOrder = vm::rememberPortfolioOrder,
    )

    // screen view-models belong to this signed-in session, not to the activity
    val store = vm.screenStore
    val owner = remember(store) {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        when (screen) {
            Screen.Portfolios -> PortfolioListScreen(nav)
            is Screen.Dashboard -> DashboardScreen(screen.portfolioId, nav)
            is Screen.Holdings -> HoldingsScreen(screen.portfolioId, nav)
            is Screen.Transactions -> TransactionsScreen(screen.portfolioId, nav)
            is Screen.Dividends -> DividendsScreen(screen.portfolioId, nav)
            is Screen.Calendar -> DividendCalendarScreen(screen.portfolioId, nav)
            is Screen.SelfFunding -> SelfFundingScreen(screen.portfolioId, nav)
            is Screen.NewTransaction -> NewTransactionScreen(screen, nav)
        }
    }
}
