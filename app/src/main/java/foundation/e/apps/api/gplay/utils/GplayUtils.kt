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

package foundation.e.apps.api.gplay.utils

import com.aurora.gplayapi.data.models.AuthData
import foundation.e.apps.login.AuthObject
import timber.log.Timber

object GplayUtils {
    suspend fun handleUnauthorizedAuthData(
        block: suspend (authData: AuthData?) -> Unit,
        authValidator: suspend () -> AuthObject?
    ): AuthObject? {
        if (!GPlayHttpClient.IS_AUTH_VALID) {
            authValidator()?.let {
                val authData = (it.result.data as AuthData)
                GPlayHttpClient.IS_AUTH_VALID = true
                block.invoke(authData)
                Timber.d("data refreshed with new auth!")
                return it
            }
        }

        return null
    }
}