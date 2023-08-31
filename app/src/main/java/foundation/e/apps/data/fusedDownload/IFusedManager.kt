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

package foundation.e.apps.data.fusedDownload

import androidx.lifecycle.LiveData
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import java.io.File

interface IFusedManager {
    fun createNotificationChannels()

    suspend fun addDownload(fusedDownload: FusedDownload)

    suspend fun getDownloadById(fusedDownload: FusedDownload): FusedDownload?

    suspend fun getDownloadList(): List<FusedDownload>
    fun getDownloadLiveList(): LiveData<List<FusedDownload>>

    suspend fun updateDownloadStatus(fusedDownload: FusedDownload, status: Status)

    suspend fun downloadApp(fusedDownload: FusedDownload)

    suspend fun installApp(fusedDownload: FusedDownload)

    suspend fun cancelDownload(fusedDownload: FusedDownload)

    suspend fun getFusedDownload(downloadId: Long = 0, packageName: String = ""): FusedDownload
    fun flushOldDownload(packageName: String)

    suspend fun downloadNativeApp(fusedDownload: FusedDownload)
    fun getGplayInstallationPackagePath(
        fusedDownload: FusedDownload,
        it: String,
        parentPath: String,
        count: Int
    ): File

    fun createObbFileForDownload(
        fusedDownload: FusedDownload,
        url: String
    ): File

    fun moveOBBFilesToOBBDirectory(fusedDownload: FusedDownload)
    fun getBaseApkPath(fusedDownload: FusedDownload): String

    suspend fun installationIssue(fusedDownload: FusedDownload)

    suspend fun updateAwaiting(fusedDownload: FusedDownload)

    suspend fun updateUnavailable(fusedDownload: FusedDownload)

    suspend fun updateFusedDownload(fusedDownload: FusedDownload)

    suspend fun insertFusedDownloadPurchaseNeeded(fusedDownload: FusedDownload)
    fun isFusedDownloadInstalled(fusedDownload: FusedDownload): Boolean
    fun getFusedDownloadInstallationStatus(fusedApp: FusedDownload): Status
}
