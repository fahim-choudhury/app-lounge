/*
 * Copyright MURENA SAS 2023
 * Apps  Quickly and easily install Android apps onto your device!
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
