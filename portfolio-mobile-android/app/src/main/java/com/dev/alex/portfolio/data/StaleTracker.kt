package com.dev.alex.portfolio.data

/**
 * Collects the answers one screen is built from and remembers the oldest cached one, so
 * the screen can say "showing data from 14:32" when any part of it came from the cache.
 */
class StaleTracker {
    private var oldestCached: Long? = null

    @Synchronized
    fun <T> take(fetched: Fetched<T>): T {
        if (fetched.fromCache) {
            oldestCached = oldestCached?.let { minOf(it, fetched.savedAt) } ?: fetched.savedAt
        }
        return fetched.value
    }

    val staleSince: Long?
        @Synchronized get() = oldestCached
}
