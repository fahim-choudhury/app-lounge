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

package foundation.e.apps.install.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.R
import foundation.e.apps.data.DownloadManager
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.fusedDownload.FusedManagerRepository
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import foundation.e.apps.install.notification.StorageNotificationManager
import foundation.e.apps.utils.eventBus.AppEvent
import foundation.e.apps.utils.eventBus.EventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.app.DownloadManager as PlatformDownloadManager
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class DownloadManagerUtils @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedManagerRepository: FusedManagerRepository,
    private val downloadManager: DownloadManager,
    private val storageNotificationManager: StorageNotificationManager,
    @Named("ioCoroutineScope") private val coroutineScope: CoroutineScope
) {
    private val mutex = Mutex()

    @DelicateCoroutinesApi
    fun cancelDownload(downloadId: Long) {
        coroutineScope.launch {
            val fusedDownload = fusedManagerRepository.getFusedDownload(downloadId)
            fusedManagerRepository.cancelDownload(fusedDownload)
        }
    }

    @DelicateCoroutinesApi
    fun updateDownloadStatus(downloadId: Long) {
        coroutineScope.launch {
            mutex.withLock {
                delay(1500) // Waiting for downloadmanager to publish the progress of last bytes
                val fusedDownload = fusedManagerRepository.getFusedDownload(downloadId)
                if (fusedDownload.id.isNotEmpty()) {

                    if (downloadManager.hasDownloadFailed(downloadId)) {
                        handleDownloadFailed(fusedDownload, downloadId)
                        Timber.e(
                            "Download failed for ${fusedDownload.packageName}, " + "reason: " + "${
                                downloadManager.getDownloadFailureReason(
                                    downloadId
                                )
                            }"
                        )
                        return@launch
                    }

                    validateDownload(fusedDownload, downloadId)
                }
            }
        }
    }

    private suspend fun handleDownloadSuccess(fusedDownload: FusedDownload) {
        Timber.i("===> Download is completed for: ${fusedDownload.name}")
        fusedManagerRepository.moveOBBFileToOBBDirectory(fusedDownload)
        if (fusedDownload.status == Status.DOWNLOADING) {
            fusedDownload.status = Status.DOWNLOADED
            fusedManagerRepository.updateFusedDownload(fusedDownload)
        }
    }

    private suspend fun handleDownloadFailed(fusedDownload: FusedDownload, downloadId: Long) {
        fusedManagerRepository.installationIssue(fusedDownload)
        fusedManagerRepository.cancelDownload(fusedDownload)
        Timber.w("===> Download failed: ${fusedDownload.name} ${fusedDownload.status}")

        if (downloadManager.getDownloadFailureReason(downloadId) == android.app.DownloadManager.ERROR_INSUFFICIENT_SPACE) {
            storageNotificationManager.showNotEnoughSpaceNotification(fusedDownload, downloadId)
            EventBus.invokeEvent(AppEvent.ErrorMessageEvent(R.string.not_enough_storage))
        }
    }

    private suspend fun validateDownload(
        fusedDownload: FusedDownload,
        downloadId: Long
    ) {
        val incompleteDownloadState = listOf(
            PlatformDownloadManager.STATUS_PENDING,
            PlatformDownloadManager.STATUS_RUNNING,
            PlatformDownloadManager.STATUS_PAUSED,
        )

        val isDownloadSuccessful = downloadManager.isDownloadSuccessful(downloadId)

        if (isDownloadSuccessful.first) {
            updateDownloadIdMap(fusedDownload, downloadId)
        }

        val numberOfDownloadedItems =
            fusedDownload.downloadIdMap.values.filter { it }.size
        Timber.d("===> updateDownloadStatus: ${fusedDownload.name}: $downloadId: $numberOfDownloadedItems/${fusedDownload.downloadIdMap.size}")

        // if download status code is unknown (-1), consider installation is failed.
        val areAllFilesDownloaded = areAllFilesDownloaded(
            numberOfDownloadedItems,
            fusedDownload
        )

        if (isDownloadSuccessful.first && areAllFilesDownloaded && checkCleanApkSignatureOK(fusedDownload)) {
            handleDownloadSuccess(fusedDownload)
            return
        }

        if (incompleteDownloadState.contains(isDownloadSuccessful.second)
            || (isDownloadSuccessful.first && !areAllFilesDownloaded)
        ) {
            return
        }

        handleDownloadFailed(fusedDownload, downloadId)
        Timber.e(
            "Download failed for ${fusedDownload.packageName}: " +
                    "Download Status: ${isDownloadSuccessful.second}"
        )
    }

    private fun areAllFilesDownloaded(
        numberOfDownloadedItems: Int,
        fusedDownload: FusedDownload
    ) =
        numberOfDownloadedItems == fusedDownload.downloadIdMap.size && numberOfDownloadedItems == fusedDownload.downloadURLList.size

    private suspend fun updateDownloadIdMap(
        fusedDownload: FusedDownload,
        downloadId: Long
    ) {
        fusedDownload.downloadIdMap[downloadId] = true
        fusedManagerRepository.updateFusedDownload(fusedDownload)
    }

    private suspend fun checkCleanApkSignatureOK(fusedDownload: FusedDownload): Boolean {
        if (fusedDownload.origin != Origin.CLEANAPK || fusedManagerRepository.isFdroidApplicationSigned(
                context, fusedDownload
            )
        ) {
            Timber.d("Apk signature is OK")
            return true
        }
        fusedDownload.status = Status.INSTALLATION_ISSUE
        fusedManagerRepository.updateFusedDownload(fusedDownload)
        Timber.w("CleanApk signature is Wrong!")
        return false
    }
}
