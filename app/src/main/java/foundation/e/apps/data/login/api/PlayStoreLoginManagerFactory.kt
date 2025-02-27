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

import com.google.gson.Gson
import foundation.e.apps.data.enums.User
import foundation.e.apps.data.playstore.utils.AC2DMTask
import foundation.e.apps.data.playstore.utils.GPlayHttpClient
import foundation.e.apps.data.preference.AppLoungeDataStore
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayStoreLoginManagerFactory @Inject constructor(
    private val gPlayHttpClient: GPlayHttpClient,
    private val nativeDeviceProperty: Properties,
    private val aC2DMTask: AC2DMTask,
    private val gson: Gson,
    private val appLoungeDataStore: AppLoungeDataStore,
) {

    fun createLoginManager(user: User): PlayStoreLoginManager {
        return when (user) {
            User.GOOGLE -> GoogleLoginManager(gPlayHttpClient, nativeDeviceProperty, aC2DMTask, appLoungeDataStore)
            else -> AnonymousLoginManager(gPlayHttpClient, nativeDeviceProperty, gson)
        }
    }
}
