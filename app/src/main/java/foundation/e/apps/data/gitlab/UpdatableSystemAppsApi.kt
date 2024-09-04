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

import foundation.e.apps.data.gitlab.models.SystemAppProject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface UpdatableSystemAppsApi {

    companion object {
        const val BASE_URL =
            "https://gitlab.e.foundation/e/os/system-apps-update-info/-/raw/main/"
    }

    enum class EndPoint(private val value: String) {
        ENDPOINT_RELEASE("updatable_system_apps.json"),
        ENDPOINT_TEST("updatable_system_apps_test.json"),
        ;
        override fun toString(): String {
            return value
        }
    }

    @GET("{endPoint}")
    suspend fun getUpdatableSystemApps(
        @Path("endPoint") endPoint: EndPoint = EndPoint.ENDPOINT_RELEASE
    ): Response<List<SystemAppProject>>

}
