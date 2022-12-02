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

package foundation.e.apps.login.api

import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.PlayResponse
import com.aurora.gplayapi.helpers.AuthHelper
import foundation.e.apps.api.gplay.utils.AC2DMTask
import foundation.e.apps.api.gplay.utils.GPlayHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

class GoogleLoginApi(
    private val gPlayHttpClient: GPlayHttpClient,
    private val nativeDeviceProperty: Properties,
    private val aC2DMTask: AC2DMTask,
) : GPlayLoginInterface {

    /**
     * Get PlayResponse for AC2DM Map. This allows us to get an error message too.
     *
     * An aasToken is extracted from this map. This is passed to [fetchAuthData]
     * to generate AuthData. This token is very important as it cannot be regenerated,
     * hence it must be saved for future use.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5680
     */
    suspend fun getAC2DMResponse(email: String, oauthToken: String): PlayResponse {
        var response = PlayResponse()
        withContext(Dispatchers.IO) {
            response = aC2DMTask.getAC2DMResponse(email, oauthToken)
        }
        return response
    }

    /**
     * Convert email and AASToken to AuthData class.
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5680
     */
    override suspend fun fetchAuthData(email: String, aasToken: String): AuthData? {
        var authData: AuthData? = null
        withContext(Dispatchers.IO) {
            authData = AuthHelper.build(email, aasToken, nativeDeviceProperty)
        }
        return authData
    }
}
