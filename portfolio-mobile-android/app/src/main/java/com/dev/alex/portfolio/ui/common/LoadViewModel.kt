package com.dev.alex.portfolio.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dev.alex.portfolio.AppContainer
import com.dev.alex.portfolio.data.StaleTracker
import com.dev.alex.portfolio.data.api.SessionExpiredException
import com.dev.alex.portfolio.data.api.describeError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoadState<T>(
    val data: T? = null,
    /** first load, nothing to show yet */
    val loading: Boolean = true,
    /** pull-to-refresh over data already on screen */
    val refreshing: Boolean = false,
    /** the last attempt failed; with [data] present the old figures stay up */
    val error: String? = null,
    /** some of [data] came from the offline cache, saved at this time */
    val staleSince: Long? = null,
)

/**
 * One screen's data: loads on first show, reloads on pull-to-refresh and after a write
 * ([AppContainer.dataVersion]), and keeps the last good figures on screen when a reload
 * fails. A session that cannot be renewed sends the whole app back to the lock screen
 * instead of showing an error.
 */
abstract class LoadViewModel<T>(protected val app: AppContainer) : ViewModel() {
    private val mutableState = MutableStateFlow(LoadState<T>())
    val state: StateFlow<LoadState<T>> = mutableState.asStateFlow()

    /** What [AutoLoad] watches, so a screen already showing reloads after a write too. */
    val dataVersion: StateFlow<Long> get() = app.dataVersion

    private var job: Job? = null
    private var jobVersion = -1L
    private var loadedAt = 0L
    private var loadedVersion = -1L

    protected abstract suspend fun load(tracker: StaleTracker): T

    /**
     * [force] = the user asked; otherwise data younger than a minute is kept, unless
     * something was written since it loaded.
     */
    fun refresh(force: Boolean = false) {
        val version = app.dataVersion.value
        if (job?.isActive == true) {
            if (jobVersion == version) return
            // started before the write: its figures would be stale on arrival
            job?.cancel()
        }
        val current = mutableState.value
        val hasData = current.data != null
        val fresh = loadedVersion == version && System.currentTimeMillis() - loadedAt < FRESH_MS
        if (!force && hasData && current.error == null && fresh) return

        mutableState.update { it.copy(loading = !hasData, refreshing = hasData) }
        jobVersion = version
        job = viewModelScope.launch {
            val tracker = StaleTracker()
            try {
                val data = load(tracker)
                loadedAt = System.currentTimeMillis()
                loadedVersion = version
                mutableState.value = LoadState(data = data, loading = false, staleSince = tracker.staleSince)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                mutableState.update { it.copy(loading = false, refreshing = false) }
                app.sessionExpired.tryEmit(Unit)
            } catch (e: Exception) {
                mutableState.update { it.copy(loading = false, refreshing = false, error = describeError(e)) }
            }
        }
    }

    private companion object {
        const val FRESH_MS = 60_000L
    }
}
