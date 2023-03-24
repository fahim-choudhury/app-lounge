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

import foundation.e.apps.api.fdroid.FdroidRepository
import foundation.e.apps.manager.database.fusedDownload.FusedDownload
import foundation.e.apps.manager.fused.FusedManagerRepository
import foundation.e.apps.manager.fused.IFusedManager
import foundation.e.apps.utils.enums.Status
import kotlinx.coroutines.delay

class FakeFusedManagerRepository(
    private val fusedDownloadDAO: FakeFusedDownloadDAO,
    fusedManager: IFusedManager,
    fdroidRepository: FdroidRepository,
) : FusedManagerRepository(fusedManager, fdroidRepository) {
    var isAppInstalled = false
    var installationStatus = Status.INSTALLED
    var willDownloadFail = false
    var willInstallFail = false
    var forceCrash = false

    override suspend fun downloadApp(fusedDownload: FusedDownload) {
        fusedDownload.status = Status.DOWNLOADING
        fusedDownload.downloadIdMap = mutableMapOf(Pair(341, false), Pair(342, false))
        fusedDownloadDAO.updateDownload(fusedDownload)
        delay(5000)

        if (willDownloadFail) {
            fusedDownload.downloadIdMap.clear()
            fusedDownload.downloadIdMap = mutableMapOf(Pair(-1, false), Pair(-1, false))
            fusedDownload.status = Status.INSTALLATION_ISSUE
            fusedDownloadDAO.updateDownload(fusedDownload)
            return
        }

        fusedDownload.downloadIdMap.replaceAll { _, _ -> true }
        fusedDownload.status = Status.DOWNLOADED
        fusedDownloadDAO.updateDownload(fusedDownload)
    }

    override suspend fun updateDownloadStatus(fusedDownload: FusedDownload, status: Status) {
        when (status) {
            Status.INSTALLING -> {
                handleStatusInstalling(fusedDownload)
            }
            Status.INSTALLED -> {
                if (forceCrash) {
                    throw RuntimeException()
                }

                fusedDownloadDAO.deleteDownload(fusedDownload)
            }
            else -> {
                fusedDownload.status = status
                fusedDownloadDAO.updateDownload(fusedDownload)
            }
        }
    }

    private suspend fun handleStatusInstalling(
        fusedDownload: FusedDownload
    ) {
        fusedDownload.status = Status.INSTALLING
        fusedDownloadDAO.updateDownload(fusedDownload)
        delay(5000)

        if (willInstallFail) {
            updateDownloadStatus(fusedDownload, Status.INSTALLATION_ISSUE)
        } else {
            updateDownloadStatus(fusedDownload, Status.INSTALLED)
        }
    }

    override suspend fun installationIssue(fusedDownload: FusedDownload) {
        fusedDownload.status = Status.INSTALLATION_ISSUE
        fusedDownloadDAO.updateDownload(fusedDownload)
    }

    override fun isFusedDownloadInstalled(fusedDownload: FusedDownload): Boolean {
        return isAppInstalled
    }

    override fun getFusedDownloadPackageStatus(fusedDownload: FusedDownload): Status {
        return installationStatus
    }
}
