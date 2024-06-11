/*
 * Copyright (C) 2024 MURENA SAS
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
 *
 */

package foundation.e.apps.data.cleanapk

import foundation.e.apps.data.cleanapk.data.app.Application
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface CleanApkAppDetailsRetrofit {

    @GET("apps?action=app_detail")
    suspend fun getAppOrPWADetailsByID(
        @Query("id") id: String,
        @Query("architectures") architectures: List<String>? = null,
        @Query("type") type: String? = null
    ): Response<Application>

    @GET("apps?action=app_detail")
    suspend fun getAppDetails(
        @Query("id") id: String
    ): Application
}
