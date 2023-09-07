package foundation.e.apps.data

import foundation.e.apps.data.gplay.utils.GPlayHttpClient
import foundation.e.apps.data.gplay.utils.GplayHttpRequestException
import foundation.e.apps.data.login.exceptions.GPlayException
import java.net.SocketTimeoutException

private const val TIMEOUT = "Timeout"
private const val UNKNOWN = "Unknown"
private const val STATUS = "Status:"
private const val ERROR_GPLAY_API = "Gplay api has faced error!"

suspend fun <T> handleNetworkResult(call: suspend () -> T): ResultSupreme<T> {
    return try {
        ResultSupreme.Success(call())
    } catch (e: SocketTimeoutException) {
        val message = extractErrorMessage(e)
        val resultTimeout = ResultSupreme.Timeout<T>(exception = e)
        resultTimeout.message = message
        resultTimeout
    } catch (e: GplayHttpRequestException) {
        val message = extractErrorMessage(e)
        val exception = GPlayException(e.status == GPlayHttpClient.STATUS_CODE_TIMEOUT, message)

        if (exception.isTimeout) {
            ResultSupreme.Timeout(exception = exception)
        } else {
            ResultSupreme.Error(message, exception)
        }
    } catch (e: Exception) {
        val message = extractErrorMessage(e)
        ResultSupreme.Error(message, e)
    }
}

private fun extractErrorMessage(e: Exception): String {
    val status = when (e) {
        is GplayHttpRequestException -> e.status.toString()
        is SocketTimeoutException -> TIMEOUT
        else -> UNKNOWN
    }
    return (e.localizedMessage?.ifBlank { ERROR_GPLAY_API }
        ?: ERROR_GPLAY_API) + " $STATUS $status"
}