/*
 * Copyright (C) 2022  ECORP
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

package foundation.e.apps.data.ecloud

import foundation.e.apps.data.ecloud.modules.FaultyToken
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface EcloudApiInterface {

    companion object {
        val BASE_URL = "https://eu.gtoken.ecloud.global/"
    }

    @Headers("Content-Type: application/json")
    @POST("report")
    suspend fun uploadFaultyEmail(
        @Body faultyToken: FaultyToken,
    )
}
