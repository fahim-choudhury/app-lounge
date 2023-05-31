package foundation.e.apps.data

import retrofit2.Response

suspend fun <T> getResult(apiCall: suspend () -> Response<T>): Result<T> {
    return try {
        fetchResult(apiCall())
    } catch (e: Exception) {
        Result.error(e.message ?: e.toString())
    }
}

private fun <T> fetchResult(response: Response<T>): Result<T> {
    if (response.isSuccessful) {
        response.body()?.let {
            return Result.success(it)
        }
    }

    return Result.error(response.message(), response.body())
}
