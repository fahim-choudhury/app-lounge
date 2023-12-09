// Copyright (C) 2022  E FOUNDATION
// SPDX-FileCopyrightText: 2023 E FOUNDATION <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package foundation.e.apps.data

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
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
import kotlin.math.abs
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
            downloadManager.query(downloadManagerQuery.setFilterById(downloadId)).use { cursor ->
                if (cursor.moveToFirst()) {
                    handleDownloadStatus(cursor, downloadId, filePath, downloadCompleted)
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private fun handleDownloadStatus(
        cursor: Cursor,
        downloadId: Long,
        filePath: String,
        downloadCompleted: ((Boolean, String) -> Unit)?
    ) {
        var status =
            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val totalSizeBytes =
            getLong(cursor, DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
        val bytesDownloadedSoFar =
            getLong(cursor, DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
        val reason =
            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))

        status = sanitizeStatus(downloadId, status, reason)

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

    fun getSizeRequired(downloadId: Long): Long {
        var totalSizeBytes = -1L
        var bytesDownloadedSoFar = -1L

        try {
            downloadManager.query(downloadManagerQuery.setFilterById(downloadId))
                .use { cursor ->
                    if (cursor.moveToFirst()) {
                        totalSizeBytes = getLong(cursor, DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        bytesDownloadedSoFar =
                            getLong(cursor, DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    }
                }
        } catch (e: RuntimeException) {
            Timber.e(e, "runtime exception on retrieving download file size.")
        }

        if (totalSizeBytes <= 0) {
            return 0
        }

        if (bytesDownloadedSoFar <= 0) {
            return totalSizeBytes
        }

        return abs(totalSizeBytes - bytesDownloadedSoFar)
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

        return sanitizeStatus(downloadId, status, reason)
    }

    private fun sanitizeStatus(downloadId: Long, status: Int, reason: Int): Int {
        if (reason <= 0) {
            return status
        }

        if (status in listOf(DownloadManager.STATUS_FAILED, DownloadManager.STATUS_PAUSED)) {
            return status
        }

        Timber.e("Download Issue: $downloadId : DownloadManager returns status: $status but the failed because: reason: $reason")

        if (reason <= DownloadManager.PAUSED_UNKNOWN) {
            return DownloadManager.STATUS_PAUSED
        }

        return DownloadManager.STATUS_FAILED
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

    private fun getLong(cursor: Cursor, column: String) =
        cursor.getLong(cursor.getColumnIndexOrThrow(column))
}
