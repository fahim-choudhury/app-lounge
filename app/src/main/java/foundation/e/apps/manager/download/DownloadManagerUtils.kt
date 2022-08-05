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

package foundation.e.apps.manager.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.manager.database.fusedDownload.FusedDownload
import foundation.e.apps.manager.fused.FusedManagerRepository
import foundation.e.apps.utils.enums.Origin
import foundation.e.apps.utils.enums.Status
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManagerUtils @Inject constructor(
    private val fusedManagerRepository: FusedManagerRepository,
    @ApplicationContext private val context: Context
) {
    private val TAG = DownloadManagerUtils::class.java.simpleName
    private val mutex = Mutex()

    @DelicateCoroutinesApi
    fun cancelDownload(downloadId: Long) {
        GlobalScope.launch {
            val fusedDownload = fusedManagerRepository.getFusedDownload(downloadId)
            fusedManagerRepository.cancelDownload(fusedDownload)
        }
    }

    @DelicateCoroutinesApi
    fun updateDownloadStatus(downloadId: Long) {
        GlobalScope.launch {
            mutex.withLock {
                delay(1500) // Waiting for downloadmanager to publish the progress of last bytes
                val fusedDownload = fusedManagerRepository.getFusedDownload(downloadId)
                if (fusedDownload.id.isNotEmpty()) {
                    fusedDownload.downloadIdMap[downloadId] = true
                    fusedManagerRepository.updateFusedDownload(fusedDownload)
                    val downloaded = fusedDownload.downloadIdMap.values.filter { it }.size
                    Timber.d("===> updateDownloadStatus: ${fusedDownload.name}: $downloadId: $downloaded/${fusedDownload.downloadIdMap.size}")
                    if (downloaded == fusedDownload.downloadIdMap.size && checkCleanApkSignatureOK(fusedDownload)) {
                        fusedManagerRepository.moveOBBFileToOBBDirectory(fusedDownload)
                        fusedDownload.status = Status.DOWNLOADED
                        fusedManagerRepository.updateFusedDownload(fusedDownload)
                    }
                }
            }
        }
    }

    private suspend fun checkCleanApkSignatureOK(fusedDownload: FusedDownload): Boolean {
        if (fusedDownload.origin != Origin.CLEANAPK || fusedManagerRepository.isFdroidApplicationSigned(
                context,
                fusedDownload
            )
        ) {
            Timber.d("Apk signature is OK")
            return true
        }
        fusedDownload.status = Status.INSTALLATION_ISSUE
        fusedManagerRepository.updateFusedDownload(fusedDownload)
        Timber.d("CleanApk signature is Wrong!")
        return false
    }
}
