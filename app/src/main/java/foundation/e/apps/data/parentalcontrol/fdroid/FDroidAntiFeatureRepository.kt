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

import foundation.e.apps.data.parentalcontrol.ContentRatingDao
import foundation.e.apps.data.parentalcontrol.FDroidNsfwApp
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FDroidAntiFeatureRepository
@Inject
constructor(
    private val fDroidMonitorApi: FDroidMonitorApi,
    private val contentRatingDao: ContentRatingDao,
) {
    var fDroidNsfwApps = listOf<String>()
        private set

    suspend fun fetchNsfwApps() {
        val fetchedFDroidNsfwApps = getFromApi()

        fDroidNsfwApps = if (fetchedFDroidNsfwApps.isEmpty()) {
            getFromDb()
        } else {
            contentRatingDao.clearFDroidNsfwApps()
            fetchedFDroidNsfwApps.also { pkgNames ->
                contentRatingDao.insertFDroidNsfwApp(pkgNames.map { FDroidNsfwApp(it) })
            }
        }
    }

    private suspend fun getFromDb(): List<String> {
        return contentRatingDao.getAllFDroidNsfwApp().map { it.packageName }
    }

    private suspend fun getFromApi(): List<String> {
        return runCatching {
            fDroidMonitorApi.getMonitorData().body()?.getNsfwApps() ?: emptyList()
        }.getOrElse { emptyList() }
    }

}
