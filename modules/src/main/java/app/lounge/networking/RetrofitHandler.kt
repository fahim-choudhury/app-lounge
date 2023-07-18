package app.lounge.networking

import retrofit2.Response

sealed class NetworkResult<T> {
    class Success<T>(val data: T) : NetworkResult<T>()
    class Error<T>(val code: Int, val message: String?) : NetworkResult<T>()
    class Exception<T>(val e: Throwable) : NetworkResult<T>()
}

suspend fun <T> getResult(call: suspend () -> Response<T>): NetworkResult<T> {
    try {
        val response = call()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) return NetworkResult.Success(body)
        }

        return NetworkResult.Error(response.code(), " ${response.code()} ${response.message()}")
    } catch (e: Exception) {
        return NetworkResult.Exception(e)
    }
}
