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

package foundation.e.apps.installProcessor

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.install.AppInstallDAO
import foundation.e.apps.data.install.models.AppInstall
import kotlinx.coroutines.flow.flow

class FakeAppInstallDAO : AppInstallDAO {
    val appInstallList = mutableListOf<AppInstall>()

    override suspend fun addDownload(appInstall: AppInstall) {
        appInstallList.add(appInstall)
    }

    override fun getDownloadLiveList(): LiveData<List<AppInstall>> {
        TODO("Not yet implemented")
    }

    override suspend fun getDownloadList(): List<AppInstall> {
        return appInstallList
    }

    override suspend fun getDownloadById(id: String): AppInstall? {
        return appInstallList.find { it.id == id }
    }

    override fun getDownloadFlowById(id: String): LiveData<AppInstall?> {
        return flow {
            while (true) {
                val fusedDownload = appInstallList.find { it.id == id }
                emit(fusedDownload)
                if (fusedDownload == null || fusedDownload.status == Status.INSTALLATION_ISSUE) {
                    break
                }
            }
        }.asLiveData()
    }

    override suspend fun updateDownload(appInstall: AppInstall) {
        appInstallList.replaceAll { if (it.id == appInstall.id) appInstall else it }
    }

    override suspend fun deleteDownload(appInstall: AppInstall) {
        appInstallList.remove(appInstall)
    }
}
