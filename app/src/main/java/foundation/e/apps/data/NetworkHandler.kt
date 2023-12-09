// Copyright MURENA SAS 2023
// SPDX-FileCopyrightText: 2023 E FOUNDATION <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package foundation.e.apps.data

import foundation.e.apps.data.playstore.utils.GPlayHttpClient
import foundation.e.apps.data.playstore.utils.GplayHttpRequestException
import foundation.e.apps.data.login.exceptions.GPlayException
import kotlinx.coroutines.delay
import timber.log.Timber
import java.net.SocketTimeoutException

private const val TIMEOUT = "Timeout"
private const val UNKNOWN = "Unknown"
private const val STATUS = "Status:"
private const val ERROR_GPLAY_API = "Gplay api has faced error!"
private const val REGEX_429_OR_401 = "429|401"
private const val MAX_RETRY_DELAY_IN_SECONDS = 300
private const val ONE_SECOND_IN_MILLIS = 1000L
private const val INITIAL_DELAY_RETRY_IN_SECONDS = 10

suspend fun <T> handleNetworkResult(call: suspend () -> T): ResultSupreme<T> {
    return try {
        ResultSupreme.Success(call())
    } catch (e: SocketTimeoutException) {
        handleSocketTimeoutException(e)
    } catch (e: GplayHttpRequestException) {
        resultSupremeGplayHttpRequestException(e)
    } catch (e: Exception) {
        handleOthersException(e)
    }
}

private fun <T> handleSocketTimeoutException(e: SocketTimeoutException): ResultSupreme.Timeout<T> {
    val message = extractErrorMessage(e)
    val resultTimeout = ResultSupreme.Timeout<T>(exception = e)
    resultTimeout.message = message
    return resultTimeout
}

private fun <T> resultSupremeGplayHttpRequestException(e: GplayHttpRequestException): ResultSupreme<T> {
    val message = extractErrorMessage(e)
    val exception = GPlayException(e.status == GPlayHttpClient.STATUS_CODE_TIMEOUT, message)

    return if (exception.isTimeout) {
        ResultSupreme.Timeout(exception = exception)
    } else {
        ResultSupreme.Error(message, exception)
    }
}

private fun <T> handleOthersException(e: Exception): ResultSupreme.Error<T> {
    val message = extractErrorMessage(e)
    return ResultSupreme.Error(message, e)
}

private fun extractErrorMessage(e: Exception): String {
    val status = when (e) {
        is GplayHttpRequestException -> e.status.toString()
        is SocketTimeoutException -> TIMEOUT
        else -> UNKNOWN
    }
    return (e.localizedMessage?.ifBlank { ERROR_GPLAY_API } ?: ERROR_GPLAY_API) + " $STATUS $status"
}

suspend fun <T> retryWithBackoff(retryDelayInSeconds: Int = -1, operation: suspend () -> T): T? {
    var result: T? = null
    try {
        if (retryDelayInSeconds > 0) {
            delay(ONE_SECOND_IN_MILLIS * retryDelayInSeconds)
        }

        result = operation()

        if (shouldRetry(result, retryDelayInSeconds)) {
            Timber.w("Retrying...: $retryDelayInSeconds")
            result = retryWithBackoff(calculateRetryDelay(retryDelayInSeconds), operation)
        }

    } catch (e: Exception) {
        Timber.e(e)
        if (retryDelayInSeconds < MAX_RETRY_DELAY_IN_SECONDS) {
            return retryWithBackoff(calculateRetryDelay(retryDelayInSeconds), operation)
        }
    }

    return result
}

private fun calculateRetryDelay(retryDelayInSecond: Int) =
    if (retryDelayInSecond < 0) INITIAL_DELAY_RETRY_IN_SECONDS else retryDelayInSecond * 2


private fun <T> shouldRetry(result: T, retryDelayInSecond: Int) =
    result is ResultSupreme<*> && !result.isSuccess() && retryDelayInSecond < MAX_RETRY_DELAY_IN_SECONDS
            && isExceptionAllowedToRetry(result.exception)

private fun isExceptionAllowedToRetry(exception: Exception?): Boolean {
    // Here, (value != true) is used, because value can be null also and we want to allow retry for null message
    return exception?.message?.contains(Regex(REGEX_429_OR_401)) != true
}
