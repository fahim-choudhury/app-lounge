/*
 * Copyright ECORP SAS 2022
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

package foundation.e.apps

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.manager.download.data.DownloadProgress
import foundation.e.apps.manager.download.data.DownloadProgressLD
import foundation.e.apps.manager.fused.FusedManagerRepository
import javax.inject.Inject

@HiltViewModel
class AppProgressViewModel @Inject constructor(
    downloadProgressLD: DownloadProgressLD,
    private val fusedManagerRepository: FusedManagerRepository
) : ViewModel() {

    val downloadProgress = downloadProgressLD

    suspend fun calculateProgress(
        fusedApp: FusedApp?,
        progress: DownloadProgress
    ): Int {
        fusedApp?.let { app ->
            val appDownload = fusedManagerRepository.getDownloadList()
                .singleOrNull { it.id.contentEquals(app._id) && it.packageName.contentEquals(app.package_name) }
                ?: return 0

            if (!appDownload.id.contentEquals(app._id) || !appDownload.packageName.contentEquals(app.package_name)) {
                return@let
            }

            if (!isProgressValidForApp(fusedApp, progress)) {
                return -1
            }

            val downloadingMap = progress.totalSizeBytes.filter { item ->
                appDownload.downloadIdMap.keys.contains(item.key) && item.value > 0
            }

            if (appDownload.downloadIdMap.size > downloadingMap.size) { // All files for download are not ready yet
                return 0
            }

            val totalSizeBytes = downloadingMap.values.sum()
            val downloadedSoFar = progress.bytesDownloadedSoFar.filter { item ->
                appDownload.downloadIdMap.keys.contains(item.key)
            }.values.sum()
            return ((downloadedSoFar / totalSizeBytes.toDouble()) * 100).toInt()
        }
        return 0
    }

    private suspend fun isProgressValidForApp(
        fusedApp: FusedApp,
        downloadProgress: DownloadProgress
    ): Boolean {
        val download = fusedManagerRepository.getFusedDownload(downloadProgress.downloadId)
        return download.id == fusedApp._id
    }
}
