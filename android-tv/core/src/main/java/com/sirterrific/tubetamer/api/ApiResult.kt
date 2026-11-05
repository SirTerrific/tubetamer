package com.sirterrific.tubetamer.api

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>

    /** The server answered with a non-2xx status. [error] is the JSON `error` code, if any. */
    data class HttpError(val code: Int, val error: String) : ApiResult<Nothing>

    /** No answer: wrong address, server down, timeout. */
    data class NetworkError(val cause: Throwable) : ApiResult<Nothing>

    /** Something answered, but not with the JSON we expect (not a TubeTamer server). */
    data class BadResponse(val cause: Throwable) : ApiResult<Nothing>
}
