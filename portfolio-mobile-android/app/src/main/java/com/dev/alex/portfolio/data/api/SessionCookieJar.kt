package com.dev.alex.portfolio.data.api

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Holds the Spring session cookie (JSESSIONID) in memory only. The app signs in again on
 * every launch — behind the biometric gate — so nothing that grants access outlives the
 * process: a cookie on disk would open the account without the fingerprint.
 */
class SessionCookieJar : CookieJar {
    private val byHost = mutableMapOf<String, MutableList<Cookie>>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val stored = byHost.getOrPut(url.host) { mutableListOf() }
        val now = System.currentTimeMillis()
        for (cookie in cookies) {
            stored.removeAll { it.name == cookie.name && it.path == cookie.path }
            if (cookie.expiresAt > now) stored.add(cookie)
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return byHost[url.host].orEmpty().filter { it.expiresAt > now && it.matches(url) }
    }

    @Synchronized
    fun clear() = byHost.clear()
}
