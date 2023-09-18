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

package foundation.e.apps.data.login

import com.aurora.gplayapi.data.models.AuthData
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.enums.User
import foundation.e.apps.data.login.exceptions.GPlayLoginException
import javax.inject.Inject
import javax.inject.Singleton

@JvmSuppressWildcards
@Singleton
class LoginSourceRepository @Inject constructor(
    private val loginCommon: LoginCommon,
    private val sources: List<LoginSourceInterface>,
) {

    var gplayAuth: AuthData? = null
        get() = field ?: throw GPlayLoginException(false, "AuthData is not available!", getUserType())

    suspend fun getAuthObjects(clearAuthTypes: List<String> = listOf()): List<AuthObject> {

        val authObjectsLocal = ArrayList<AuthObject>()

        for (source in sources) {
            if (!source.isActive()) continue
            if (source::class.java.simpleName in clearAuthTypes) {
                source.clearSavedAuth()
            }

            val authObject = source.getAuthObject()
            authObjectsLocal.add(authObject)

            if (authObject is AuthObject.GPlayAuth) {
                gplayAuth = authObject.result.data
            }
        }

        return authObjectsLocal
    }

    suspend fun saveUserType(user: User) {
        loginCommon.saveUserType(user)
    }

    suspend fun saveGoogleLogin(email: String, oauth: String) {
        loginCommon.saveGoogleLogin(email, oauth)
    }

    suspend fun setNoGoogleMode() {
        loginCommon.setNoGoogleMode()
    }

    suspend fun logout() {
        loginCommon.logout()
    }

    suspend fun getValidatedAuthData(): ResultSupreme<AuthData?> {
        val authDataValidator = (sources.find { it is AuthDataValidator } as AuthDataValidator)
        val validateAuthData = authDataValidator.validateAuthData()
        this.gplayAuth = validateAuthData.data
        return validateAuthData
    }

    private fun getUserType(): User {
        return loginCommon.getUserType()
    }
}
