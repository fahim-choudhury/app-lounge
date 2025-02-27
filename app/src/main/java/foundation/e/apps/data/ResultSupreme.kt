/*
 * Copyright (C) 2022  ECORP
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package foundation.e.apps.data

import foundation.e.apps.data.enums.ResultStatus
import java.util.concurrent.TimeoutException

private const val UNKNOWN_ERROR = "Unknown error!"

/**
 * Another implementation of Result class.
 * This removes the use of [ResultStatus] class for different status.
 * This class also follows the standard code patterns. However, we still have the same
 * flaw that [data] is nullable. As such we may have to add extra null checks or just
 * brute force with !!
 *
 * Also since for each case we now use an inner class with slightly different name,
 * we need some refactoring.
 *
 * Issue: https://gitlab.e.foundation/e/os/backlog/-/issues/313
 */
sealed class ResultSupreme<T> {

    /**
     * Success case.
     * Use [isSuccess] to check.
     *
     * @param data End result of processing.
     */
    class Success<T>(data: T) : ResultSupreme<T>() {
        init { setData(data) }
    }

    /**
     * Timed out during network related job.
     * Use [isTimeout] to check.
     *
     * @param data The process is expected to output some blank data, but it cannot be null.
     * Example can be an empty list.
     * @param exception Optional exception from try-catch block.
     */
    class Timeout<T>(data: T? = null, exception: Exception = TimeoutException()) :
        ResultSupreme<T>() {
        init {
            data?.let {
                setData(it)
            }
            this.exception = exception
        }
    }

    /**
     * Miscellaneous error case.
     * No valid data from processing.
     * Use [isUnknownError] to check.
     */
    open class Error<T>() : ResultSupreme<T>() {
        /**
         * @param message A String message to log or display to the user.
         * @param exception Optional exception from try-catch block.
         */
        constructor(message: String, exception: Exception? = null) : this() {
            this.message = message
            this.exception = exception
        }

        /**
         * @param data Non-null data. Example a String which could not be parsed into a JSON.
         * @param message A optional String message to log or display to the user.
         */
        constructor(data: T, message: String = "") : this() {
            setData(data)
            this.message = message
        }
    }

    class WorkError<T> constructor(data: T, payload: Any? = null) : Error<T>(data) {
        init {
            this.otherPayload = payload
        }
    }

    /**
     * Data from processing. May be null.
     */
    var data: T? = null
        private set

    /**
     * A custom string message for logging or displaying to the user.
     */
    var message: String = ""

    var otherPayload: Any? = null

    /**
     * Exception from try-catch block for error cases.
     */
    var exception: Exception? = null

    fun isValidData() = data != null

    fun isSuccess() = this is Success && isValidData()
    fun isTimeout() = this is Timeout
    fun isUnknownError() = this is Error

    fun setData(data: T) {
        this.data = data
    }

    fun getResultStatus(): ResultStatus {
        return when (this) {
            is Success -> ResultStatus.OK
            is Timeout -> ResultStatus.TIMEOUT
            else -> ResultStatus.UNKNOWN.apply {
                message = this@ResultSupreme.exception?.localizedMessage ?: UNKNOWN_ERROR
            }
        }
    }

    companion object {

        /**
         * Function to create an instance of ResultSupreme from a [ResultStatus] status,
         * and other available info - [data], [message], [exception].
         */
        fun <T> create(
            status: ResultStatus,
            data: T? = null,
            message: String = "",
            exception: Exception? = null,
        ): ResultSupreme<T> {
            val resultObject = when {
                status == ResultStatus.OK && data != null -> Success<T>(data)
                status == ResultStatus.TIMEOUT && data != null -> Timeout<T>(data)
                else -> Error(message.ifBlank { status.message }, exception)
            }
            resultObject.apply {
                if (isUnknownError()) {
                    this.data = data
                } else {
                    this.message = message.ifBlank { status.message }
                    this.exception = exception
                }
            }
            return resultObject
        }

        /**
         * Create a similar [ResultSupreme] instance i.e. of type [Success], [Timeout]...
         * using a supplied [result] object but with a different generic type and new data.
         *
         * @param result Class of [ResultSupreme] whose replica is to be made.
         * @param newData Nullable new data for this replica.
         * @param message Optional new message for this replica. If not provided,
         * the new object will get the message from [result].
         * @param exception Optional new exception for this replica. If not provided,
         * the new object will get the exception from [result].
         */
        fun <T> replicate(
            result: ResultSupreme<*>,
            newData: T?,
            message: String? = null,
            exception: Exception? = null,
        ): ResultSupreme<T> {
            val status = when (result) {
                is Success -> ResultStatus.OK
                is Timeout -> ResultStatus.TIMEOUT
                is Error -> ResultStatus.UNKNOWN
            }
            return create(
                status, newData, message ?: result.message,
                exception ?: result.exception
            )
        }
    }
}
