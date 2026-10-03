package com.dev.alex.portfolio.ui.common

import com.dev.alex.portfolio.data.api.SessionExpiredException
import kotlinx.coroutines.CancellationException

/**
 * For a part of a screen that may fail on its own (saved settings, one ticker's statistics)
 * without taking the screen down. Cancellation and an expired session still propagate.
 */
suspend fun <T> optional(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: SessionExpiredException) {
    throw e
} catch (e: Exception) {
    null
}
