package com.dev.alex.portfolio.data.api

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Who is signed in, against which server. The password is kept in memory for the life of
 * the process so a session that times out (Spring's 30 min idle) can be renewed without
 * asking again; it never touches disk here — the only stored copy is the keystore-encrypted
 * one in CredentialStore, which needs a fingerprint to open.
 */
class SessionManager {
    @Volatile var baseUrl: HttpUrl? = null
        private set
    @Volatile var username: String? = null
        private set
    @Volatile private var password: String? = null

    fun begin(baseUrl: HttpUrl, username: String, password: String) {
        this.baseUrl = baseUrl
        this.username = username
        this.password = password
    }

    fun credentials(): Pair<String, String>? {
        val user = username ?: return null
        val pass = password ?: return null
        return user to pass
    }

    fun clear() {
        baseUrl = null
        username = null
        password = null
    }

    companion object {
        /**
         * "pc.tailnet.ts.net" → "https://pc.tailnet.ts.net/". Keeps a path prefix, so a
         * backend served under one ("https://host/portfolio") works too.
         */
        fun parseServerUrl(input: String): HttpUrl {
            val trimmed = input.trim().trimEnd('/')
            if (trimmed.isEmpty()) throw InvalidServerException("Enter the server address.")
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            val url = withScheme.toHttpUrlOrNull()
                ?: throw InvalidServerException("\"$input\" is not a valid server address.")
            return url.newBuilder().query(null).fragment(null).build()
        }
    }
}
