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
 * Contains common function for first login, logout, get which type of authentication / source
 * to be used etc...
 *
 * https://gitlab.e.foundation/e/backlog/-/issues/5680
 */
@Singleton
class LoginCommon @Inject constructor(
    private val loginDataStore: LoginDataStore,
) {

    fun getAuthTypes(): List<String> {
        if (loginDataStore.getUserType() == User.UNAVAILABLE) {
            return emptyList()
        }
        return ArrayList<String>().apply {
            if (loginDataStore.isGplaySelected()) {
                add(AuthObject.GPlayAuth::class.java.simpleName)
            }
            if (loginDataStore.isPWASelected() || loginDataStore.isOpenSourceSelected()) {
                add(AuthObject.CleanApk::class.java.simpleName)
            }
        }
    }

    suspend fun saveUserType(user: User) {
        loginDataStore.saveUserType(user)
    }

    suspend fun saveGoogleLogin(email: String, oauth: String) {
        loginDataStore.saveGoogleLogin(email, oauth)
    }

    suspend fun logout() {
        loginDataStore.destroyCredentials()
        loginDataStore.clearUserType()
    }
}
