/*
 * Copyright MURENA SAS 2023
 * Apps  Quickly and easily install Android apps onto your device!
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


package app.lounge.login.anonymous

import app.lounge.extension.toByteArray
import app.lounge.model.AnonymousAuthDataRequestBody
import app.lounge.networking.NetworkResult
import app.lounge.networking.fetch
import com.aurora.gplayapi.data.models.AuthData
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import javax.inject.Inject
import javax.inject.Singleton

interface AnonymousUserRetrofitAPI {

    companion object {
        const val tokenBaseURL: String = "https://eu.gtoken.ecloud.global"
    }

    @POST(Path.authData)
    suspend fun authDataRequest(
        @HeaderMap headers: Map<String, String>,
        @Body requestBody: RequestBody
    ): Response<AuthData>

    object Header {
        val authData: (() -> String) -> Map<String, String> = {
            mapOf(Pair("User-Agent", it.invoke()))
        }
    }

    private object Path {
        const val authData = "/"
    }

}

@Singleton
class AnonymousUserRetrofitImpl @Inject constructor(
    val eCloud: Retrofit
) : AnonymousUser {

    private val eCloudRetrofitAPI = eCloud.create(
        AnonymousUserRetrofitAPI::class.java
    )

    override suspend fun requestAuthData(
        anonymousAuthDataRequestBody: AnonymousAuthDataRequestBody
    ): NetworkResult<AuthData> {
        val requestBody: RequestBody =
            anonymousAuthDataRequestBody.properties.toByteArray().let { result ->
                result.toRequestBody(
                    contentType = "application/json".toMediaTypeOrNull(),
                    offset = 0,
                    byteCount = result.size
                )
            }
        return fetch {
            eCloudRetrofitAPI.authDataRequest(
                requestBody = requestBody,
                headers = AnonymousUserRetrofitAPI.Header.authData {
                    anonymousAuthDataRequestBody.userAgent
                }
            )
        }
    }

}