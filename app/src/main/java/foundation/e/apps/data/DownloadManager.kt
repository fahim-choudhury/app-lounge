// Copyright (C) 2022  E FOUNDATION
// SPDX-FileCopyrightText: 2023 E FOUNDATION <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package foundation.e.apps.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.OpenForTesting
import foundation.e.apps.R
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
@OpenForTesting
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    @Named("cacheDir") private val cacheDir: String,
    private val downloadManagerQuery: DownloadManager.Query,
) {
    private val downloadsMaps = HashMap<Long, Boolean>()

    private val SDCARD_PATH = Environment.getExternalStorageDirectory().absolutePath
    val EXTERNAL_STORAGE_TEMP_CACHE_DIR = "$SDCARD_PATH/Download/AppLounge/SplitInstallApks"

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
        if (downloadFile.exists()) {
            try {
                downloadFile.delete()
            } catch (exception: Exception) {
                Timber.e("Could not delete already existing split apk: $downloadFile", exception)
            }
        }

        return downloadFile(url, downloadFile, downloadCompleted)
    }

    private fun downloadFile(
        url: String,
        downloadFile: File,
        downloadCompleted: ((Boolean, String) -> Unit)?
    ): Long {
        var downloadId = -1L
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(context.getString(R.string.downloading))
                .setDestinationUri(Uri.fromFile(downloadFile))
            downloadId = downloadManager.enqueue(request)
        } catch (e: java.lang.NullPointerException) {
            Timber.e(e, "Url: $url; downloadFilePath: ${downloadFile.absolutePath}")
            downloadCompleted?.invoke(false, e.localizedMessage ?: "No message found!")
            return downloadId
        }

        downloadsMaps[downloadId] = true
        tickerFlow(downloadId, .5.seconds).onEach {
            checkDownloadProgress(downloadId, downloadFile.absolutePath, downloadCompleted)
        }.launchIn(CoroutineScope(Dispatchers.IO))
        return downloadId
    }

    fun checkDownloadProgress(
        downloadId: Long,
        filePath: String = "",
        downloadCompleted: ((Boolean, String) -> Unit)?
    ) {
        try {
            downloadManager.query(downloadManagerQuery.setFilterById(downloadId))
                .use { cursor ->
                    if (cursor.moveToFirst()) {
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

    fun hasDownloadFailed(downloadId: Long): Boolean {
        return getDownloadStatus(downloadId) == DownloadManager.STATUS_FAILED
    }

    private fun getDownloadStatus(downloadId: Long): Int {
        var status = -1
        var reason = -1
        try {
            downloadManager.query(downloadManagerQuery.setFilterById(downloadId))
                .use { cursor ->
                    if (cursor.moveToFirst()) {
                        status =
                            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        reason =
                            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        Timber.d("Download Status: downloadId: $downloadId $status")
                    }
                }
        } catch (e: Exception) {
            Timber.e(e)
        }

        if (status != DownloadManager.STATUS_SUCCESSFUL) {
            Timber.e("Download Issue: $downloadId status: $status reason: $reason")
        }
        return status
    }

    fun getDownloadFailureReason(downloadId: Long): Int {
        var reason = -1
        try {
            downloadManager.query(downloadManagerQuery.setFilterById(downloadId))
                .use { cursor ->
                    if (cursor.moveToFirst()) {
                        reason =
                            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    }
                }
        } catch (e: Exception) {
            Timber.e(e)
        }
        return reason
    }
}
