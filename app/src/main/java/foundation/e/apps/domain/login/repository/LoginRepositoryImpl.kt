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

package foundation.e.apps.domain.login.repository

import android.content.Context
import app.lounge.login.anonymous.AnonymousUser
import app.lounge.model.AnonymousAuthDataRequestBody
import app.lounge.networking.NetworkResult
import app.lounge.storage.cache.configurations
import com.aurora.gplayapi.data.models.AuthData
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class LoginRepositoryImpl @Inject constructor(
    private val anonymousUser: AnonymousUser,
    @ApplicationContext val applicationContext: Context
) : LoginRepository {

    override suspend fun anonymousUser(authDataRequestBody: AnonymousAuthDataRequestBody): AuthData {
        val result = anonymousUser.requestAuthData(
            anonymousAuthDataRequestBody = authDataRequestBody
        )

        when (result) {
            is NetworkResult.Error ->
                throw Exception(result.errorMessage, result.exception)
            is NetworkResult.Success -> {
                applicationContext.configurations.authData = result.data.toString()
                return result.data
            }
        }
    }
}
