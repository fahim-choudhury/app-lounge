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

package foundation.e.apps.data.parentalcontrol.fdroid

import retrofit2.Response
import retrofit2.http.GET

interface FDroidMonitorApi {

    companion object {
        const val BASE_URL = "https://f-droid.org/repo/"
    }

    @GET("status/update.json")
    suspend fun getMonitorData(): Response<FDroidMonitorData>

}
