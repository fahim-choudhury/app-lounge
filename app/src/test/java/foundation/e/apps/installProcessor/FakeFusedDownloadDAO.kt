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
import foundation.e.apps.data.database.fusedDownload.FusedDownload
import foundation.e.apps.data.database.fusedDownload.FusedDownloadDAO

class FakeFusedDownloadDAO : FusedDownloadDAO {
    val fusedDownloadList = mutableListOf<FusedDownload>()

    override suspend fun addDownload(fusedDownload: FusedDownload) {
        fusedDownloadList.add(fusedDownload)
    }

    override fun getDownloadLiveList(): LiveData<List<FusedDownload>> {
        TODO("Not yet implemented")
    }

    override suspend fun getDownloadList(): List<FusedDownload> {
        return fusedDownloadList
    }

    override suspend fun getDownloadById(id: String): FusedDownload? {
        return fusedDownloadList.find { it.id == id }
    }

    override fun getDownloadFlowById(id: String): LiveData<FusedDownload> {
        TODO("Not yet implemented")
    }

    override suspend fun updateDownload(fusedDownload: FusedDownload) {
        fusedDownloadList.replaceAll { if (it.id == fusedDownload.id) fusedDownload else it }
    }

    override suspend fun deleteDownload(fusedDownload: FusedDownload) {
        fusedDownloadList.remove(fusedDownload)
    }
}
