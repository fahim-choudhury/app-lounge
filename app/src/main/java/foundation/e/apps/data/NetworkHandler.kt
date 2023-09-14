// Copyright MURENA SAS 2023
// SPDX-FileCopyrightText: 2023 E FOUNDATION <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

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
