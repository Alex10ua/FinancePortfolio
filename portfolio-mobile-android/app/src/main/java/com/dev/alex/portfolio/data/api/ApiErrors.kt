package com.dev.alex.portfolio.data.api

import java.io.IOException

/** Failures the server reported. Transport failures stay plain [IOException]s. */
sealed class ApiException(message: String) : Exception(message)

/** `POST /login` answered 401. */
class InvalidCredentialsException : ApiException("Wrong username or password.")

/** A request answered 401 and signing in again did not help. */
class SessionExpiredException : ApiException("Your session ended. Sign in again.")

/** 404 — for the optional endpoints (statistics) this means "no data", not an error. */
class NotFoundException : ApiException("Not found.")

class HttpStatusException(val code: Int) : ApiException("The server answered HTTP $code.")

/** No server address entered yet, or it is not a valid URL. */
class InvalidServerException(message: String) : ApiException(message)

/**
 * A write the backend turned down with a 4xx, carrying Spring's `{"error": …}` text. The
 * create endpoint validates before it saves, so nothing was stored.
 */
class RequestRejectedException(val code: Int, message: String) : ApiException(message)

/** True when showing the last cached copy is the right answer to [error]. */
fun isOfflineError(error: Throwable): Boolean =
    error is IOException || (error is HttpStatusException && error.code >= 500)

/**
 * True only when [error] proves the request never left the phone (no route, no
 * connection, no TLS session), so a write cannot have been stored. A timeout or a
 * dropped connection proves nothing: the server may have saved it.
 */
fun wasNeverSent(error: Throwable): Boolean =
    error is java.net.UnknownHostException ||
        error is java.net.ConnectException ||
        error is java.net.NoRouteToHostException ||
        error is javax.net.ssl.SSLHandshakeException

/** One line for the UI, without a stack trace. */
fun describeError(error: Throwable): String = when (error) {
    is ApiException -> error.message ?: "Request failed."
    is java.net.UnknownHostException -> "Can't find the server. Is Tailscale connected?"
    is java.net.ConnectException -> "Can't connect to the server."
    is java.net.SocketTimeoutException -> "The server took too long to answer."
    is javax.net.ssl.SSLException -> "Secure connection failed: ${error.message ?: "TLS error"}."
    is IOException -> "Network error: ${error.message ?: error.javaClass.simpleName}."
    is kotlinx.serialization.SerializationException -> "The server sent data this app can't read."
    else -> error.message ?: error.javaClass.simpleName
}
