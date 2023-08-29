package app.lounge.networking

import kotlinx.coroutines.withContext

private const val UNKNOWN_ERROR_ = "Unknown Error!"

suspend fun <T> fetchGooglePlayApi(call: suspend () -> T): NetworkResult<T> {
    return try {
        NetworkResult.Success(call())
    } catch (e: GplayException) {
        NetworkResult.Error(e, e.errorCode, e.localizedMessage ?: UNKNOWN_ERROR_)
    } catch (e: Exception) {
        NetworkResult.Error(e, e.hashCode(), e.localizedMessage ?: UNKNOWN_ERROR_)
    }
}