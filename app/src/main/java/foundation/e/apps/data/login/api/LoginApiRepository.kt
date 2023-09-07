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
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.enums.User
import foundation.e.apps.data.gplay.utils.AC2DMUtil
import foundation.e.apps.data.handleNetworkResult
import foundation.e.apps.data.login.exceptions.GPlayLoginException
import java.util.Locale

/**
 * Call methods of [GoogleLoginApi] and [AnonymousLoginApi] from here.
 *
 * Dependency Injection via hilt is not possible,
 * we need to manually check login type, create an instance of either [GoogleLoginApi]
 * or [AnonymousLoginApi] and pass it to [gPlayLoginInterface].
 *
 * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5680
 */
class LoginApiRepository constructor(
    private val gPlayLoginInterface: GPlayLoginInterface,
    private val user: User,
) {

    /**
     * Gets the auth data from instance of [GPlayLoginInterface].
     * Applicable for both Google and Anonymous login.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5680
     * @param email Email address for Google login. Blank for Anonymous login.
     * @param aasToken For Google login - Access token obtained from [getAasToken] function,
     * else blank for Anonymous login.
     */
    suspend fun fetchAuthData(email: String, aasToken: String, locale: Locale): ResultSupreme<AuthData?> {
        val result = handleNetworkResult {
            gPlayLoginInterface.fetchAuthData(email, aasToken)
        }
        return result.apply {
            this.data?.locale = locale
            this.exception = when (result) {
                is ResultSupreme.Timeout -> GPlayLoginException(true, "GPlay API timeout", user)
                is ResultSupreme.Error -> GPlayLoginException(false, result.message, user)
                else -> null
            }
        }
    }

    /**
     * Get AuthData validity of in the form of PlayResponse.
     * Advantage of not using a simple boolean is we get error message and
     * network code of the request inside PlayResponse object.
     *
     * Applicable for both Google and Anonymous login.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5680
     */
    suspend fun login(authData: AuthData): ResultSupreme<PlayResponse> {
        var response = PlayResponse()
        val result = handleNetworkResult {
            response = gPlayLoginInterface.login(authData)
            if (response.code != 200) {
                throw Exception("Validation network code: ${response.code}")
            }
            response
        }
        return ResultSupreme.replicate(result, response).apply {
            this.exception = when (result) {
                is ResultSupreme.Timeout -> GPlayLoginException(true, "GPlay API timeout", user)
                is ResultSupreme.Error -> GPlayLoginException(false, result.message, user)
                else -> null
            }
        }
    }

    /**
     * Gets email and oauthToken from Google login, finds the AASToken from AC2DM response
     * and returns it. This token is then used to fetch AuthData from [fetchAuthData].
     *
     * Do note that for a given oauthToken, it has been observed that AASToken can
     * only be generated once. So this token must be saved for future use.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5680
     *
     * @param googleLoginApi An instance of [GoogleLoginApi] must be passed, this method
     * cannot work on [gPlayLoginInterface] as it is a common interface for both Google and Anonymous
     * login, but this method is only for Google login.
     */
    suspend fun getAasToken(
        googleLoginApi: GoogleLoginApi,
        email: String,
        oauthToken: String
    ): ResultSupreme<String> {
        val result = handleNetworkResult {
            var aasToken = ""
            val response = googleLoginApi.getAC2DMResponse(email, oauthToken)
            var error = response.errorString
            if (response.isSuccessful) {
                val responseMap = AC2DMUtil.parseResponse(String(response.responseBytes))
                aasToken = responseMap["Token"] ?: ""
                if (aasToken.isBlank() && error.isBlank()) {
                    error = "AASToken not found in map."
                }
            }
            /*
             * Default value of PlayResponse.errorString is "No Error".
             * https://gitlab.com/AuroraOSS/gplayapi/-/blob/master/src/main/java/com/aurora/gplayapi/data/models/PlayResponse.kt
             */
            if (error != "No Error") {
                throw Exception(error)
            }
            aasToken
        }
        return result.apply {
            this.exception = when (result) {
                is ResultSupreme.Timeout -> GPlayLoginException(true, "GPlay API timeout", User.GOOGLE)
                is ResultSupreme.Error -> GPlayLoginException(false, result.message, User.GOOGLE)
                else -> null
            }
        }
    }
}
