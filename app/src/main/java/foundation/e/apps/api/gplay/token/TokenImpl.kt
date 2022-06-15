/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2021  E FOUNDATION
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

package foundation.e.apps.api.gplay.token

import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.helpers.AuthHelper
import com.google.gson.Gson
import foundation.e.apps.api.gplay.utils.GPlayHttpClient
import timber.log.Timber
import java.util.Properties
import javax.inject.Inject

class TokenImpl @Inject constructor(
    private val nativeDeviceProperty: Properties,
    private val gson: Gson,
    private val gPlayHttpClient: GPlayHttpClient
) {

    companion object {
        const val BASE_URL = "https://eu.gtoken.ecloud.global"
    }

    fun getAuthData(): AuthData? {
        val playResponse =
            gPlayHttpClient.postAuth(BASE_URL, gson.toJson(nativeDeviceProperty).toByteArray())
        return if(playResponse.isSuccessful) {
            gson.fromJson(String(playResponse.responseBytes), AuthData::class.java)
        } else {
            Timber.d(">>> getAuthData: UNSUCCESSFUL > ${playResponse.errorString}")
            null
        }
    }

    fun getAuthData(email: String, aasToken: String): AuthData {
        return AuthHelper.build(email, aasToken, nativeDeviceProperty)
    }
}
