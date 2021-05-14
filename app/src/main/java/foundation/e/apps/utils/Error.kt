/*
 * Copyright (C) 2019-2021  E FOUNDATION
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

package foundation.e.apps.utils

import foundation.e.apps.R
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Class containing various errors that can occur in the project
 * @param description Type of error from this class
 */
enum class Error(val description: Int) {
    NO_RESULTS(R.string.error_no_results),
    NO_INTERNET(R.string.error_no_internet),
    SERVER_UNAVAILABLE(R.string.error_server_unavailable),
    REQUEST_TIMEOUT(R.string.error_request_timeout),
    UNKNOWN(R.string.error_unknown),
    APK_UNAVAILABLE(R.string.error_apk_unavailable),
    INVALID_PACKAGE_NAME(R.string.error_invalid_package_name),
    PACKAGE_ALREADY_EXISTS(R.string.error_package_already_exists),
    NO_ERROR(R.string.error_no_error),
    APK_INCOMPATIBLE(R.string.error_apk_incompatible);

    private class CustomException(val error: Error) : Exception(error.name.lowercase())

    companion object {
        /**
         * Finds the type of error which occurred using the given exception
         * @param e [Exception]
         * @return The type of the [Error]
         */
        fun findError(e: Exception): Error {
            return when (e::class) {
                CustomException::class -> (e as CustomException).error
                SocketTimeoutException::class -> return REQUEST_TIMEOUT
                IOException::class -> return SERVER_UNAVAILABLE
                else -> UNKNOWN
            }
        }
    }
}
