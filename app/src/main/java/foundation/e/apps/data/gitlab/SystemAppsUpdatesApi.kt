/*
 * Copyright (C) 2019-2023  E FOUNDATION
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

package foundation.e.apps.data.gitlab

import foundation.e.apps.data.gitlab.models.EligibleSystemApps
import foundation.e.apps.data.gitlab.models.UpdateDefinition
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface SystemAppsUpdatesApi {

    companion object {
        const val BASE_URL =
            "https://gitlab.e.foundation/e/os/update-assets/-/raw/main/"
    }

    @GET("eligible_apps.json")
    suspend fun getAllEligibleApps(): Response<List<EligibleSystemApps>>

    @GET("{packageName}/{releaseType}.json")
    suspend fun getSystemAppUpdateInfo(
        @Path("packageName") packageName: String,
        @Path("releaseType") releaseType: String,
    ): Response<UpdateDefinition>

}