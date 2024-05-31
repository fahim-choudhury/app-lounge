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

package foundation.e.apps.fusedManager

import androidx.lifecycle.LiveData
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.install.AppInstallDAO
import foundation.e.apps.data.install.AppManager
import foundation.e.apps.data.install.models.AppInstall
import java.io.File

class FakeAppManager(private val appInstallDAO: AppInstallDAO) : AppManager {
    override fun createNotificationChannels() {
        TODO("Not yet implemented")
    }

    override suspend fun addDownload(appInstall: AppInstall) {
        appInstall.status = Status.QUEUED
        appInstallDAO.addDownload(appInstall)
    }

    override suspend fun getDownloadById(appInstall: AppInstall): AppInstall? {
        return appInstallDAO.getDownloadById(appInstall.id)
    }

    override suspend fun getDownloadList(): List<AppInstall> {
        TODO("Not yet implemented")
    }

    override fun getDownloadLiveList(): LiveData<List<AppInstall>> {
        TODO("Not yet implemented")
    }

    override suspend fun updateDownloadStatus(appInstall: AppInstall, status: Status) {
        TODO("Not yet implemented")
    }

    override suspend fun downloadApp(appInstall: AppInstall) {
        TODO("Not yet implemented")
    }

    override suspend fun installApp(appInstall: AppInstall) {
        TODO("Not yet implemented")
    }

    override suspend fun cancelDownload(appInstall: AppInstall) {
        TODO("Not yet implemented")
    }

    override suspend fun getFusedDownload(downloadId: Long, packageName: String): AppInstall {
        TODO("Not yet implemented")
    }

    override fun flushOldDownload(packageName: String) {
        TODO("Not yet implemented")
    }

    override suspend fun downloadNativeApp(appInstall: AppInstall) {
        TODO("Not yet implemented")
    }

    override fun getGplayInstallationPackagePath(
        appInstall: AppInstall,
        it: String,
        parentPath: String,
        count: Int
    ): File {
        TODO("Not yet implemented")
    }

    override fun createObbFileForDownload(appInstall: AppInstall, url: String): File {
        TODO("Not yet implemented")
    }

    override fun moveOBBFilesToOBBDirectory(appInstall: AppInstall) {
        TODO("Not yet implemented")
    }

    override fun getBaseApkPath(appInstall: AppInstall): String {
        return "root/data/apps/${appInstall.packageName}/${appInstall.packageName}_1.apk"
    }

    override suspend fun installationIssue(appInstall: AppInstall) {
        TODO("Not yet implemented")
    }

    override suspend fun updateAwaiting(appInstall: AppInstall) {
        TODO("Not yet implemented")
    }

    override suspend fun updateUnavailable(appInstall: AppInstall) {
        TODO("Not yet implemented")
    }

    override suspend fun updateAppInstall(appInstall: AppInstall) {
        TODO("Not yet implemented")
    }

    override suspend fun insertAppInstallPurchaseNeeded(appInstall: AppInstall) {
        TODO("Not yet implemented")
    }

    override fun isAppInstalled(appInstall: AppInstall): Boolean {
        TODO("Not yet implemented")
    }

    override fun getInstallationStatus(appInstall: AppInstall): Status {
        TODO("Not yet implemented")
    }
}
