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

import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.enums.User
import foundation.e.apps.data.preference.AppLoungeDataStore
import foundation.e.apps.data.preference.AppLoungePreference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Just a dummy class for CleanApk, as it requires no authentication.
 * https://gitlab.e.foundation/e/backlog/-/issues/5680
 */
@Singleton
class CleanApkAuthenticator @Inject constructor(
    private val appLoungeDataStore: AppLoungeDataStore,
    private val appLoungePreference: AppLoungePreference,
) : StoreAuthenticator {

    private val user: User
        get() = appLoungeDataStore.getUserType()

    override fun isStoreActive(): Boolean {
        if (user == User.UNAVAILABLE) {
            /*
             * UNAVAILABLE user means first login is not completed.
             */
            return false
        }
        return appLoungePreference.isOpenSourceSelected() || appLoungePreference.isPWASelected()
    }

    override suspend fun login(): AuthObject.CleanApk {
        return AuthObject.CleanApk(
            ResultSupreme.Success(Unit),
            user,
        )
    }

    override suspend fun logout() {}
}
