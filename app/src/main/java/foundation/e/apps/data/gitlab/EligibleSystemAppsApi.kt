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

import foundation.e.apps.data.gitlab.models.SystemAppProject
import retrofit2.Response
import retrofit2.http.GET

interface EligibleSystemAppsApi {

    companion object {
        const val BASE_URL =
            "https://gitlab.e.foundation/e/os/blocklist-app-lounge/-/raw/7846-apk_publish_poc/"
    }

    @GET("updatable_system_apps.json")
    suspend fun getAllEligibleApps(): Response<List<SystemAppProject>>

}