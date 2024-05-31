/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2021  E FOUNDATION
 * Copyright (C) 2023  MURENA SAS
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

package foundation.e.apps.install.notification

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.R
import foundation.e.apps.data.DownloadManager
import foundation.e.apps.data.install.models.AppInstall
import foundation.e.apps.di.NotificationManagerModule
import foundation.e.apps.utils.StorageComputer
import javax.inject.Inject

class StorageNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
) {
    companion object {
        const val NOT_ENOUGH_SPACE_NOTIFICATION_ID = 7874
    }

    fun showNotEnoughSpaceNotification(appInstall: AppInstall, downloadId: Long? = null) {

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val content = getNotEnoughSpaceNotificationContent(appInstall, downloadId)

            notify(
                NOT_ENOUGH_SPACE_NOTIFICATION_ID,
                getNotEnoughSpaceNotification(content)
            )
        }
    }

    private fun getNotEnoughSpaceNotificationContent(
        appInstall: AppInstall,
        downloadId: Long? = null
    ): String {
        val requiredInByte = getSpaceMissing(appInstall, downloadId)

        if (requiredInByte <= 0L) {
            return context.getString(R.string.not_enough_storage)
        }

        return context.getString(
            R.string.free_space_for_update,
            StorageComputer.humanReadableByteCountSI(requiredInByte)
        )
    }

    private fun getSpaceMissing(appInstall: AppInstall, downloadId: Long? = null): Long {
        if (appInstall.appSize > 0L) {
            return calculateSpaceMissingFromFusedDownload(appInstall)
        }

        if (downloadId == null) {
            return 0
        }

        return downloadManager.getSizeRequired(downloadId)
    }

    private fun calculateSpaceMissingFromFusedDownload(appInstall: AppInstall): Long {
        var requiredInByte = StorageComputer.spaceMissing(appInstall)
        if (requiredInByte <= 0L) {
            requiredInByte = appInstall.appSize
        }

        return requiredInByte
    }

    private fun getNotEnoughSpaceNotification(content: String): Notification {
        return NotificationCompat.Builder(context, NotificationManagerModule.DOWNLOADS)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSmallIcon(R.drawable.app_lounge_notification_icon)
            .build()
    }
}
