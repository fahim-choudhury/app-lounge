/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2023 MURENA SAS
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

package foundation.e.apps.utils

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteFullException
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.R
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomUncaughtExceptionHandler @Inject constructor(
    @ApplicationContext private val context: Context
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val NOTIFICATION_ID = 404
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        Timber.e(throwable, "unhandled exception is caught at thread: ${thread.name}")

        if (throwable is SQLiteFullException || throwable.cause is SQLiteFullException) {
            showNotification(R.string.notification_content_full_db)
        }
    }

    private fun showNotification(@StringRes contentId: Int) {
        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        createNotificationChannel()

        val notification = getNotification(contentId)
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun getNotification(@StringRes contentId: Int): Notification {
        val content = context.getString(contentId)
        val channelId = context.getString(R.string.warning_notification_channel_id)
        val title = context.getString(R.string.app_name)

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.app_lounge_notification_icon)
            .setContentTitle(title)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentText(content).build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val id = context.getString(R.string.warning_notification_channel_id)
            val title = context.getString(R.string.warning_notification_channel_title)

            val channel = NotificationChannel(
                id, title, NotificationManager.IMPORTANCE_DEFAULT
            )

            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
