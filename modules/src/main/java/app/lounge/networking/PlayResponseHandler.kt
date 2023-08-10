package app.lounge.networking

import app.lounge.gplay.GplayException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun <T> fetchPlayResponse(call: suspend () -> T) : NetworkResult<T> {
    return withContext(Dispatchers.IO) {
        try {
            val result = call()
            NetworkResult.Success(result)
        } catch (e: GplayException) {
            NetworkResult.Error(e, e.errorCode, e.localizedMessage)
        } catch (e: Exception) {
            NetworkResult.Error(e, -1, e.localizedMessage)
        }
    }
}