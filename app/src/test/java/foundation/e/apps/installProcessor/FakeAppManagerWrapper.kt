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

import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.fdroid.FdroidRepository
import foundation.e.apps.data.install.AppManagerWrapper
import foundation.e.apps.data.install.AppManager
import foundation.e.apps.data.install.models.AppInstall
import kotlinx.coroutines.delay

class FakeAppManagerWrapper(
    private val fusedDownloadDAO: FakeAppInstallDAO,
    fusedManager: AppManager,
    fdroidRepository: FdroidRepository,
) : AppManagerWrapper(fusedManager, fdroidRepository) {
    var isAppInstalled = false
    var installationStatus = Status.INSTALLED
    var willDownloadFail = false
    var willInstallFail = false
    var forceCrash = false

    override suspend fun downloadApp(appInstall: AppInstall) {
        if (forceCrash) {
            System.out.println("Throwing test exception")
            throw Exception("test exception!")
        }

        appInstall.status = Status.DOWNLOADING
        appInstall.downloadIdMap = mutableMapOf(Pair(341, false), Pair(342, false))
        fusedDownloadDAO.updateDownload(appInstall)
        delay(5000)

        if (willDownloadFail) {
            appInstall.downloadIdMap.clear()
            appInstall.downloadIdMap = mutableMapOf(Pair(-1, false), Pair(-1, false))
            appInstall.status = Status.INSTALLATION_ISSUE
            fusedDownloadDAO.updateDownload(appInstall)
            return
        }

        appInstall.downloadIdMap.replaceAll { _, _ -> true }
        appInstall.status = Status.DOWNLOADED
        fusedDownloadDAO.updateDownload(appInstall)
        updateDownloadStatus(appInstall, Status.INSTALLING)
    }

    override suspend fun updateDownloadStatus(appInstall: AppInstall, status: Status) {
        when (status) {
            Status.INSTALLING -> {
                handleStatusInstalling(appInstall)
            }
            Status.INSTALLED -> {
                fusedDownloadDAO.deleteDownload(appInstall)
            }
            else -> {
                appInstall.status = status
                fusedDownloadDAO.updateDownload(appInstall)
            }
        }
    }

    private suspend fun handleStatusInstalling(
        appInstall: AppInstall
    ) {
        appInstall.status = Status.INSTALLING
        fusedDownloadDAO.updateDownload(appInstall)
        delay(5000)

        if (willInstallFail) {
            updateDownloadStatus(appInstall, Status.INSTALLATION_ISSUE)
        } else {
            updateDownloadStatus(appInstall, Status.INSTALLED)
        }
    }

    override suspend fun installationIssue(appInstall: AppInstall) {
        appInstall.status = Status.INSTALLATION_ISSUE
        fusedDownloadDAO.updateDownload(appInstall)
    }

    override fun isFusedDownloadInstalled(appInstall: AppInstall): Boolean {
        return isAppInstalled
    }

    override fun getFusedDownloadPackageStatus(appInstall: AppInstall): Status {
        return installationStatus
    }

    override suspend fun cancelDownload(appInstall: AppInstall) {
        fusedDownloadDAO.deleteDownload(appInstall)
    }
}
