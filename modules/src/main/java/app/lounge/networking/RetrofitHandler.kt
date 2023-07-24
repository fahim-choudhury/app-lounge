package app.lounge.networking

import retrofit2.Response

sealed interface NetworkResult<T> {
    data class Success<T>(val data: T) : NetworkResult<T>
    data class Error<T>(
        val exception: Throwable,
        val code: Int,
        val errorMessage: String,
    ) : NetworkResult<T>
}

suspend fun <T> fetch(call: suspend () -> Response<T>): NetworkResult<T> {
    try {
        val response = call()
        if (response.isSuccessful) {
            response.body()?.let { result ->
                return NetworkResult.Success(result)
            }
        }

        return NetworkResult.Error(
            exception = Exception(response.message()),
            code = response.code(),
            errorMessage = " ${response.code()} ${response.message()}"
        )
    } catch (exception: Exception) {
        return NetworkResult.Error(
            exception = exception,
            code = exception.hashCode(),
            errorMessage = exception.toString()
        )
    }
}
