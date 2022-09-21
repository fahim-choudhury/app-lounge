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

package foundation.e.apps.login

import foundation.e.apps.utils.enums.User
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Perform various login tasks using login sources.
 * https://gitlab.e.foundation/e/backlog/-/issues/5680
 */
@JvmSuppressWildcards
@Singleton
class LoginSourceRepository @Inject constructor(
    private val loginCommon: LoginCommon,
    private val sources: List<LoginSourceInterface>,
) {

    suspend fun getAuthObjects(clearAuthTypes: List<String> = listOf()): List<AuthObject> {

        val authObjectsLocal = ArrayList<AuthObject>()

        for (source in sources) {
            if (!source.isActive()) continue
            if (source::class.java.simpleName in clearAuthTypes) {
                source.clearSavedAuth()
            }
            authObjectsLocal.add(source.getAuthObject())
        }

        return authObjectsLocal
    }

    suspend fun saveUserType(user: User) {
        loginCommon.saveUserType(user)
    }

    suspend fun saveGoogleLogin(email: String, oauth: String) {
        loginCommon.saveGoogleLogin(email, oauth)
    }

    suspend fun logout() {
        loginCommon.logout()
    }
}
