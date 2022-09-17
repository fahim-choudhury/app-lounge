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

package foundation.e.apps.updates.manager

import com.aurora.gplayapi.data.models.AuthData
import foundation.e.apps.api.fused.MemoryDao
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.utils.enums.ResultStatus
import javax.inject.Inject

class UpdatesManagerRepository @Inject constructor(
    private val updatesManagerImpl: UpdatesManagerImpl
) {

    suspend fun getUpdates(authData: AuthData): Pair<List<FusedApp>, ResultStatus> {
        if (MemoryDao.hasAnyAppsForUpdate()) {
            return Pair(MemoryDao.appsAwaitingForUpdate, ResultStatus.OK)
        }
        return updatesManagerImpl.getUpdates(authData).run {
            val filteredApps = first.filter { !(!it.isFree && authData.isAnonymous) }
            MemoryDao.appsAwaitingForUpdate = filteredApps
            Pair(filteredApps, this.second)
        }
    }

    fun getApplicationCategoryPreference(): String {
        return updatesManagerImpl.getApplicationCategoryPreference()
    }
}
