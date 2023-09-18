/*
 *  Copyright (C) 2022  Murena SAS
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package foundation.e.apps.data.preference

import com.aurora.gplayapi.data.models.AuthData
import com.google.gson.Gson
import foundation.e.apps.data.enums.User
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreManager @Inject constructor() {
    @Inject
    lateinit var dataStoreModule: DataStoreModule

    @Inject
    lateinit var gson: Gson

    fun getAuthData(): AuthData {
        val authDataJson = dataStoreModule.getAuthDataSync()
        return gson.fromJson(authDataJson, AuthData::class.java) ?: AuthData("", "")
    }

    fun getUserType(): User {
        return dataStoreModule.getUserType()
    }

    fun getAuthDataJson(): String {
        return dataStoreModule.getAuthDataSync()
    }
}