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
import foundation.e.apps.data.install.AppManagerWrapper
import foundation.e.apps.data.install.models.AppInstall
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
    private val appManagerWrapper: AppManagerWrapper,
    private val downloadManager: DownloadManager,
    private val storageNotificationManager: StorageNotificationManager,
    @Named("ioCoroutineScope") private val coroutineScope: CoroutineScope
) {
    private val mutex = Mutex()

    @DelicateCoroutinesApi
    fun cancelDownload(downloadId: Long) {
        coroutineScope.launch {
            val fusedDownload = appManagerWrapper.getFusedDownload(downloadId)
            appManagerWrapper.cancelDownload(fusedDownload)
        }
    }

    @DelicateCoroutinesApi
    fun updateDownloadStatus(downloadId: Long) {
        coroutineScope.launch {
            mutex.withLock {
                delay(1500) // Waiting for downloadmanager to publish the progress of last bytes
                val fusedDownload = appManagerWrapper.getFusedDownload(downloadId)
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

    private suspend fun handleDownloadSuccess(appInstall: AppInstall) {
        Timber.i("===> Download is completed for: ${appInstall.name}")
        appManagerWrapper.moveOBBFileToOBBDirectory(appInstall)
        if (appInstall.status == Status.DOWNLOADING) {
            appInstall.status = Status.DOWNLOADED
            appManagerWrapper.updateFusedDownload(appInstall)
        }
    }

    private suspend fun handleDownloadFailed(appInstall: AppInstall, downloadId: Long) {
        appManagerWrapper.installationIssue(appInstall)
        appManagerWrapper.cancelDownload(appInstall)
        Timber.w("===> Download failed: ${appInstall.name} ${appInstall.status}")

        if (downloadManager.getDownloadFailureReason(downloadId) == android.app.DownloadManager.ERROR_INSUFFICIENT_SPACE) {
            storageNotificationManager.showNotEnoughSpaceNotification(appInstall, downloadId)
            EventBus.invokeEvent(AppEvent.ErrorMessageEvent(R.string.not_enough_storage))
        }
    }

    private suspend fun validateDownload(
        appInstall: AppInstall,
        downloadId: Long
    ) {
        val incompleteDownloadState = listOf(
            PlatformDownloadManager.STATUS_PENDING,
            PlatformDownloadManager.STATUS_RUNNING,
            PlatformDownloadManager.STATUS_PAUSED,
        )

        val isDownloadSuccessful = downloadManager.isDownloadSuccessful(downloadId)

        if (isDownloadSuccessful.first) {
            updateDownloadIdMap(appInstall, downloadId)
        }

        val numberOfDownloadedItems =
            appInstall.downloadIdMap.values.filter { it }.size

        Timber.d("===> updateDownloadStatus: ${appInstall.name}: $downloadId: $numberOfDownloadedItems/${appInstall.downloadIdMap.size}")

        val areAllFilesDownloaded = areAllFilesDownloaded(
            numberOfDownloadedItems,
            appInstall
        )

        if (isDownloadSuccessful.first && areAllFilesDownloaded && checkCleanApkSignatureOK(appInstall)) {
            handleDownloadSuccess(appInstall)
            return
        }

        if (incompleteDownloadState.contains(isDownloadSuccessful.second)
            || (isDownloadSuccessful.first && !areAllFilesDownloaded)
        ) {
            return
        }

        handleDownloadFailed(appInstall, downloadId)
        Timber.e(
            "Download failed for ${appInstall.packageName}: " +
                    "Download Status: ${isDownloadSuccessful.second}"
        )
    }

    private fun areAllFilesDownloaded(
        numberOfDownloadedItems: Int,
        appInstall: AppInstall
    ) =
        numberOfDownloadedItems == appInstall.downloadIdMap.size && numberOfDownloadedItems == appInstall.downloadURLList.size

    private suspend fun updateDownloadIdMap(
        appInstall: AppInstall,
        downloadId: Long
    ) {
        appInstall.downloadIdMap[downloadId] = true
        appManagerWrapper.updateFusedDownload(appInstall)
    }

    private suspend fun checkCleanApkSignatureOK(appInstall: AppInstall): Boolean {
        if (appInstall.origin != Origin.CLEANAPK || appManagerWrapper.isFdroidApplicationSigned(
                context, appInstall
            )
        ) {
            Timber.d("Apk signature is OK")
            return true
        }
        appInstall.status = Status.INSTALLATION_ISSUE
        appManagerWrapper.updateFusedDownload(appInstall)
        Timber.w("CleanApk signature is Wrong!")
        return false
    }
}
