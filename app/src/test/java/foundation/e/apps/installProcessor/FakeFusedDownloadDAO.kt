// Copyright MURENA SAS 2023
// SPDX-FileCopyrightText: 2023 MURENA SAS <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package foundation.e.apps.installProcessor

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.fusedDownload.FusedDownloadDAO
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import kotlinx.coroutines.flow.flow

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

    override fun getDownloadFlowById(id: String): LiveData<FusedDownload?> {
        return flow {
            while (true) {
                val fusedDownload = fusedDownloadList.find { it.id == id }
                emit(fusedDownload)
                if (fusedDownload == null || fusedDownload.status == Status.INSTALLATION_ISSUE) {
                    break
                }
            }
        }.asLiveData()
    }

    override suspend fun updateDownload(fusedDownload: FusedDownload) {
        fusedDownloadList.replaceAll { if (it.id == fusedDownload.id) fusedDownload else it }
    }

    override suspend fun deleteDownload(fusedDownload: FusedDownload) {
        fusedDownloadList.remove(fusedDownload)
    }
}
