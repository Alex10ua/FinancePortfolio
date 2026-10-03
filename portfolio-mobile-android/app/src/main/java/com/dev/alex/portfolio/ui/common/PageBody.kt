package com.dev.alex.portfolio.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dev.alex.portfolio.AppContainer
import com.dev.alex.portfolio.PortfolioApp
import com.dev.alex.portfolio.ui.components.ErrorState
import com.dev.alex.portfolio.ui.components.LoadingSkeleton
import com.dev.alex.portfolio.ui.components.StatusBanner

/** A screen's view-model, built from the app container and keyed per portfolio. */
@Composable
inline fun <reified VM : ViewModel> screenViewModel(key: String, crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as PortfolioApp).container
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

/** Loads on first show, when data is older than a minute, and after every write. */
@Composable
fun <T> AutoLoad(vm: LoadViewModel<T>) {
    val version by vm.dataVersion.collectAsStateWithLifecycle()
    LaunchedEffect(vm, version) { vm.refresh() }
}

/**
 * Scrolling page body with pull-to-refresh, and the four states every page has: first
 * load (skeleton), failure with nothing cached (error), and data — fresh, from the offline
 * cache, or kept on screen after a failed refresh (banner above it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> PageBody(
    state: LoadState<T>,
    onRefresh: () -> Unit,
    content: @Composable ColumnScope.(T) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 14.dp),
        ) {
            val data = state.data
            when {
                data != null -> {
                    StatusBanner(state.staleSince, state.error, onRefresh)
                    content(data)
                }
                state.error != null -> ErrorState(state.error, onRefresh)
                else -> LoadingSkeleton()
            }
        }
    }
}
