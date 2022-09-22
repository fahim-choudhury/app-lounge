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

import foundation.e.apps.api.ResultSupreme
import foundation.e.apps.utils.enums.User
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Just a dummy class for CleanApk, as it requires no authentication.
 * https://gitlab.e.foundation/e/backlog/-/issues/5680
 */
@Singleton
class LoginSourceCleanApk @Inject constructor(
    val loginDataStore: LoginDataStore,
) : LoginSourceInterface {

    private val user: User
        get() = loginDataStore.getUserType()

    override fun isActive(): Boolean {
        if (user == User.UNAVAILABLE) {
            /*
             * UNAVAILABLE user means first login is not completed.
             */
            return false
        }
        return loginDataStore.isOpenSourceSelected() || loginDataStore.isPWASelected()
    }

    override suspend fun getAuthObject(): AuthObject.CleanApk {
        return AuthObject.CleanApk(
            ResultSupreme.Success(Unit),
            user,
        )
    }

    override suspend fun clearSavedAuth() {}
}
