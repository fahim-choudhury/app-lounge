/*
 * Copyright (C) 2019-2022  E FOUNDATION
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

package foundation.e.apps.data.login.api

import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.PlayResponse
import com.google.gson.Gson
import foundation.e.apps.data.playstore.utils.CustomAuthValidator
import foundation.e.apps.data.playstore.utils.GPlayHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

class AnonymousLoginManager(
    private val gPlayHttpClient: GPlayHttpClient,
    private val nativeDeviceProperty: Properties,
    private val gson: Gson,
) : PlayStoreLoginManager {

    private val tokenUrl: String = "https://eu.gtoken.ecloud.global"

    /**
     * Log anonymously a user
     *
     * @return authData: authentication data
     */
    override suspend fun login(): AuthData? {
        var authData: AuthData? = null
        withContext(Dispatchers.IO) {
            val response =
                gPlayHttpClient.postAuth(tokenUrl, gson.toJson(nativeDeviceProperty).toByteArray())
            if (response.code != 200 || !response.isSuccessful) {
                throw Exception(
                    "Error fetching Anonymous credentials\n" +
                        "Network code: ${response.code}\n" +
                        "Success: ${response.isSuccessful}" +
                        response.errorString.run {
                            if (isNotBlank()) "\nError message: $this"
                            else ""
                        }
                )
            } else {
                authData = gson.fromJson(
                    String(response.responseBytes),
                    AuthData::class.java
                )
            }
        }
        return authData
    }

    /**
     * Check if an AuthData is valid. Returns a [PlayResponse].
     * Check [PlayResponse.isSuccessful] to see if the validation was successful.
     */
    override suspend fun validate(authData: AuthData): PlayResponse {
        var result = PlayResponse()
        withContext(Dispatchers.IO) {
            try {
                val authValidator = CustomAuthValidator(authData).using(gPlayHttpClient)
                result = authValidator.getValidityResponse()
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
        return result
    }
}
