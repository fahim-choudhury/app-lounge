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
import foundation.e.apps.data.enums.User
import foundation.e.apps.utils.SystemInfoProvider
import foundation.e.apps.utils.toJsonString
import java.util.Properties
import javax.inject.Inject

class LoginRepositoryImpl @Inject constructor(
    @ApplicationContext val applicationContext: Context,
    private val properties: Properties,
    private val anonymousUser: AnonymousUser
) : LoginRepository {

    private val userAgent: String by lazy { SystemInfoProvider.getAppBuildInfo() }

    override suspend fun anonymousUser(): AuthData {
        val result = anonymousUser.requestAuthData(
            anonymousAuthDataRequestBody = AnonymousAuthDataRequestBody(
                properties = properties,
                userAgent = userAgent
            )
        )

        when (result) {
            is NetworkResult.Error ->
                throw Exception(result.errorMessage, result.exception)
            is NetworkResult.Success -> {
                val authData = result.data
                applicationContext.configurations.authData = authData.toJsonString()
                applicationContext.configurations.userType = User.ANONYMOUS.name
                return authData
            }
        }
    }

    // TODO: Remove function parameter once we refactor Google User APIs
    override suspend fun googleUser(authData: AuthData, token: String) {
        applicationContext.configurations.authData = authData.toJsonString()
        applicationContext.configurations.email = authData.email
        applicationContext.configurations.oauthtoken = token
        applicationContext.configurations.userType = User.GOOGLE.name
    }
}
