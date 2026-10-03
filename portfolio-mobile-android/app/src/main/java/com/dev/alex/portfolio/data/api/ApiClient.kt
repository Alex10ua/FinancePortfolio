package com.dev.alex.portfolio.data.api

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The backend's session-cookie API: GETs, plus [post] for the app's one write (a new
 * transaction). Add another write only on purpose.
 *
 * Auth is Spring form login: `POST /login` with `username`/`password` answers 200 and sets
 * JSESSIONID; any request without a live session answers 401 (HttpStatusEntryPoint). A
 * 401 mid-session is answered by signing in once more with the in-memory credentials and
 * retrying the request once.
 */
class ApiClient(
    private val client: OkHttpClient,
    private val session: SessionManager,
    private val cookies: SessionCookieJar,
) {
    private val loginLock = Mutex()
    /** bumped on every successful sign-in, so N requests failing together re-login once */
    @Volatile private var loginGeneration = 0L

    /**
     * For writes. OkHttp re-sends a request by itself when a pooled connection turns out to
     * be dead, and a re-sent POST books the transaction twice — so no silent retry, and no
     * idle connection kept to go stale. The long read timeout covers a first trade in a new
     * ticker, which the backend answers only after a synchronous market-data fetch.
     */
    private val writeClient: OkHttpClient by lazy {
        client.newBuilder()
            .retryOnConnectionFailure(false)
            .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
            .readTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    suspend fun login(baseUrl: HttpUrl, username: String, password: String) {
        val body = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .build()
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegment("login").build())
            .post(body)
            .build()
        client.newCall(request).await().use { response ->
            when {
                response.code == 401 -> throw InvalidCredentialsException()
                !response.isSuccessful -> throw HttpStatusException(response.code)
                else -> Unit
            }
        }
        loginGeneration++
    }

    /** Best effort: the session dies with the process anyway. */
    suspend fun logout() {
        val base = session.baseUrl ?: return
        val request = Request.Builder()
            .url(base.newBuilder().addPathSegment("logout").build())
            .post(FormBody.Builder().build())
            .build()
        runCatching { client.newCall(request).await().close() }
        cookies.clear()
    }

    /**
     * GET `/api/v1/{segments…}` and return the body text. Each segment is encoded on its
     * own — a portfolioId routinely carries spaces and punctuation.
     */
    suspend fun get(segments: List<String>, query: Map<String, String> = emptyMap()): String {
        val url = apiUrl(segments, query)
        val generation = loginGeneration
        var response = execute(url)
        if (response.code == 401) {
            response.close()
            renewSession(generation)
            response = execute(url)
        }
        response.use {
            when {
                it.code == 401 -> throw SessionExpiredException()
                it.code == 404 -> throw NotFoundException()
                !it.isSuccessful -> throw HttpStatusException(it.code)
            }
            return it.body?.string().orEmpty()
        }
    }

    /**
     * POST a JSON [json] body to `/api/v1/{segments…}` and return the response text. Sent
     * at most twice, and the second time only after a 401: Spring answers that from its
     * filter chain before any controller runs, so nothing was stored by the first.
     */
    suspend fun post(segments: List<String>, json: String): String {
        val request = Request.Builder()
            .url(apiUrl(segments, emptyMap()))
            .header("Accept", "application/json")
            .post(json.toRequestBody(JSON))
            .build()

        val generation = loginGeneration
        var response = writeClient.newCall(request).await()
        if (response.code == 401) {
            response.close()
            renewSession(generation)
            response = writeClient.newCall(request).await()
        }
        response.use {
            val text = it.body?.string().orEmpty()
            when {
                it.code == 401 -> throw SessionExpiredException()
                it.code in 400..499 -> throw RequestRejectedException(
                    it.code,
                    serverError(text) ?: "The server refused it (HTTP ${it.code}).",
                )
                !it.isSuccessful -> throw HttpStatusException(it.code)
            }
            return text
        }
    }

    private fun apiUrl(segments: List<String>, query: Map<String, String>): HttpUrl {
        val base = session.baseUrl ?: throw SessionExpiredException()
        return base.newBuilder()
            .addPathSegments("api/v1")
            .apply {
                segments.forEach { addPathSegment(it) }
                query.forEach { (name, value) -> addQueryParameter(name, value) }
            }
            .build()
    }

    /** The `error` text of GlobalExceptionHandler's `{"error": …}` body. */
    private fun serverError(body: String): String? = runCatching {
        ApiJson.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private suspend fun execute(url: HttpUrl): Response =
        client.newCall(
            Request.Builder().url(url).header("Accept", "application/json").get().build(),
        ).await()

    private suspend fun renewSession(seenGeneration: Long) = loginLock.withLock {
        // another request already signed in again while this one waited for the lock
        if (loginGeneration != seenGeneration) return@withLock
        val base = session.baseUrl ?: throw SessionExpiredException()
        val (user, pass) = session.credentials() ?: throw SessionExpiredException()
        try {
            login(base, user, pass)
        } catch (e: InvalidCredentialsException) {
            throw SessionExpiredException()
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val WRITE_TIMEOUT_SECONDS = 120L
    }
}

/** OkHttp's enqueue as a cancellable suspend call. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resume(response) else response.close()
        }
    })
    continuation.invokeOnCancellation { runCatching { cancel() } }
}
