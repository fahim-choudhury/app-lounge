/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2022  E FOUNDATION
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
package foundation.e.apps.api

import android.app.DownloadManager
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Singleton
class DownloadManager @Inject constructor(
    private val downloadManager: DownloadManager,
    @Named("cacheDir") private val cacheDir: String,
    private val downloadManagerQuery: DownloadManager.Query,
) {
    private val downloadsMaps = HashMap<Long, Boolean>()

    companion object {
        const val EXTERNAL_STORAGE_TEMP_CACHE_DIR = "/sdcard/Download/AppLounge/SplitInstallApks"
    }

    fun downloadFileInCache(
        url: String,
        subDirectoryPath: String = "",
        fileName: String,
        downloadCompleted: ((Boolean, String) -> Unit)?
    ): Long {
        val directoryFile = File("$cacheDir/$subDirectoryPath")
        if (!directoryFile.exists()) {
            directoryFile.mkdirs()
        }

        val downloadFile = File("$cacheDir/$fileName")

        return downloadFile(url, downloadFile, downloadCompleted)
    }

    fun downloadFileInExternalStorage(
        url: String,
        subDirectoryPath: String,
        fileName: String,
        downloadCompleted: ((Boolean, String) -> Unit)?
    ): Long {

        val directoryFile = File("$EXTERNAL_STORAGE_TEMP_CACHE_DIR/$subDirectoryPath")
        if (!directoryFile.exists()) {
            directoryFile.mkdirs()
        }

        val downloadFile = File("$directoryFile/$fileName")

        return downloadFile(url, downloadFile, downloadCompleted)
    }

    private fun downloadFile(
        url: String,
        downloadFile: File,
        downloadCompleted: ((Boolean, String) -> Unit)?
    ): Long {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Downloading...")
            .setDestinationUri(Uri.fromFile(downloadFile))
        val downloadId = downloadManager.enqueue(request)
        downloadsMaps[downloadId] = true
        tickerFlow(downloadId, .5.seconds).onEach {
            checkDownloadProgress(downloadId, downloadFile.absolutePath, downloadCompleted)
        }.launchIn(CoroutineScope(Dispatchers.IO))
        return downloadId
    }

    private fun checkDownloadProgress(
        downloadId: Long,
        filePath: String = "",
        downloadCompleted: ((Boolean, String) -> Unit)?
    ) {
        try {
            downloadManager.query(downloadManagerQuery.setFilterById(downloadId))
                .use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id =
                            cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                        val status =
                            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val totalSizeBytes =
                            cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val bytesDownloadedSoFar =
                            cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        if (status == DownloadManager.STATUS_FAILED) {
                            Timber.d("Download Failed: $filePath=> $bytesDownloadedSoFar/$totalSizeBytes $status")
                            downloadsMaps[downloadId] = false
                            downloadCompleted?.invoke(false, filePath)
                        } else if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            Timber.d("Download Successful: $filePath=> $bytesDownloadedSoFar/$totalSizeBytes $status")
                            downloadsMaps[downloadId] = false
                            downloadCompleted?.invoke(true, filePath)
                        }
                    }
                }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private fun tickerFlow(
        downloadId: Long,
        period: Duration,
        initialDelay: Duration = Duration.ZERO
    ) = flow {
        delay(initialDelay)
        while (downloadsMaps[downloadId]!!) {
            emit(Unit)
            delay(period)
        }
    }

    fun isDownloadSuccessful(downloadId: Long): Boolean {
        return getDownloadStatus(downloadId) == DownloadManager.STATUS_SUCCESSFUL
    }

    private fun getDownloadStatus(downloadId: Long): Int {
        try {
            downloadManager.query(downloadManagerQuery.setFilterById(downloadId))
                .use { cursor ->
                    if (cursor.moveToFirst()) {
                        val status =
                            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        Timber.d("Download Failed: downloadId: $downloadId $status")
                        return status
                    }
                }
        } catch (e: Exception) {
            Timber.e(e)
        }
        return DownloadManager.STATUS_FAILED
    }

    suspend fun checkDownloadProcess(downloadingIds: LongArray, handleFailed: suspend () -> Unit) {
        try {
            downloadManager.query(downloadManagerQuery.setFilterById(*downloadingIds))
                .use { cursor ->
                    if (!cursor.moveToFirst()) {
                        return@use
                    }
                    while (!cursor.isAfterLast) {
                        val status =
                            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

                        if (status == DownloadManager.STATUS_FAILED) {
                            handleFailed()
                        }

                        cursor.moveToNext()
                    }
                }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }
}
