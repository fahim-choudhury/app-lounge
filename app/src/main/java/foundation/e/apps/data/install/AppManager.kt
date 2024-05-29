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

package foundation.e.apps.data.install

import androidx.lifecycle.LiveData
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.install.models.AppInstall
import java.io.File

interface AppManager {
    fun createNotificationChannels()

    suspend fun addDownload(appInstall: AppInstall)

    suspend fun getDownloadById(appInstall: AppInstall): AppInstall?

    suspend fun getDownloadList(): List<AppInstall>
    fun getDownloadLiveList(): LiveData<List<AppInstall>>

    suspend fun updateDownloadStatus(appInstall: AppInstall, status: Status)

    suspend fun downloadApp(appInstall: AppInstall)

    suspend fun installApp(appInstall: AppInstall)

    suspend fun cancelDownload(appInstall: AppInstall)

    suspend fun getFusedDownload(downloadId: Long = 0, packageName: String = ""): AppInstall
    fun flushOldDownload(packageName: String)

    suspend fun downloadNativeApp(appInstall: AppInstall)
    fun getGplayInstallationPackagePath(
        appInstall: AppInstall,
        it: String,
        parentPath: String,
        count: Int
    ): File

    fun createObbFileForDownload(
        appInstall: AppInstall,
        url: String
    ): File

    fun moveOBBFilesToOBBDirectory(appInstall: AppInstall)
    fun getBaseApkPath(appInstall: AppInstall): String

    suspend fun installationIssue(appInstall: AppInstall)

    suspend fun updateAwaiting(appInstall: AppInstall)

    suspend fun updateUnavailable(appInstall: AppInstall)

    suspend fun updateAppInstall(appInstall: AppInstall)

    suspend fun insertAppInstallPurchaseNeeded(appInstall: AppInstall)
    fun isAppInstalled(appInstall: AppInstall): Boolean
    fun getInstallationStatus(appInstall: AppInstall): Status
}
