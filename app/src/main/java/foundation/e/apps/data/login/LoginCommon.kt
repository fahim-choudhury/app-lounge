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

import foundation.e.apps.data.Constants
import foundation.e.apps.data.enums.User
import foundation.e.apps.data.preference.DataStoreModule
import foundation.e.apps.data.preference.PreferenceManagerModule
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
    private val dataStoreModule: DataStoreModule,
    private val preferenceManagerModule: PreferenceManagerModule,
) {
    suspend fun saveUserType(user: User) {
        dataStoreModule.saveUserType(user)
    }

    fun getUserType(): User {
        return dataStoreModule.getUserType()
    }

    suspend fun saveGoogleLogin(email: String, oauth: String) {
        dataStoreModule.saveGoogleLogin(email, oauth)
    }

    suspend fun setNoGoogleMode() {
        preferenceManagerModule.setSource(Constants.PREFERENCE_SHOW_FOSS, true)
        preferenceManagerModule.setSource(Constants.PREFERENCE_SHOW_PWA, true)
        preferenceManagerModule.setSource(Constants.PREFERENCE_SHOW_GPLAY, false)
        dataStoreModule.saveUserType(User.NO_GOOGLE)
    }

    suspend fun logout() {
        dataStoreModule.destroyCredentials()
        dataStoreModule.saveUserType(null)
        // reset app source preferences on logout.
        preferenceManagerModule.setSource(Constants.PREFERENCE_SHOW_FOSS, true)
        preferenceManagerModule.setSource(Constants.PREFERENCE_SHOW_PWA, true)
        preferenceManagerModule.setSource(Constants.PREFERENCE_SHOW_GPLAY, true)
    }
}
