/*
 * Copyright (C) 2021-2024 MURENA SAS
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

import foundation.e.apps.data.gitlab.models.SystemAppInfo
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface SystemAppDefinitionApi {

    companion object {
        const val BASE_URL =
            "https://gitlab.e.foundation/api/v4/projects/"
    }

    @GET("{projectId}/releases/permalink/latest/downloads/json/{releaseType}.json")
    suspend fun getSystemAppUpdateInfo(
        @Path("projectId") projectId: Int,
        @Path("releaseType") releaseType: String,
    ): Response<SystemAppInfo>

}
