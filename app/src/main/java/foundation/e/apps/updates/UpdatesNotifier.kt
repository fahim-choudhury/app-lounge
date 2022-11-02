/*
 * Copyright (C) 2019-2022  E FOUNDATION
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

package foundation.e.apps.updates

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import foundation.e.apps.MainActivity
import foundation.e.apps.R

object UpdatesNotifier {
    const val UPDATES_NOTIFICATION_CLICK_EXTRA = "updates_notification_click_extra"
    private const val UPDATES_NOTIFICATION_ID = 76
    private const val UPDATES_NOTIFICATION_CHANNEL_ID = "updates_notification"
    private const val UPDATES_NOTIFICATION_CHANNEL_TITLE = "App updates"

    fun getNotification(
        context: Context,
        numberOfApps: Int,
        installAutomatically: Boolean,
        unmeteredNetworkOnly: Boolean,
        isConnectedToUnmeteredNetwork: Boolean
    ): Notification {
        val notificationBuilder =
            createNotificationBuilder(context)

        when (numberOfApps) {
            0 -> {
                notificationBuilder.setContentTitle(
                    "Checking Updates..."
                )
            }
            1 -> {
                notificationBuilder.setContentTitle(
                    context.resources.getQuantityString(
                        R.plurals.updates_notification_title,
                        1,
                        numberOfApps
                    )
                )
            }
            else -> {
                notificationBuilder.setContentTitle(
                    context.resources.getQuantityString(
                        R.plurals.updates_notification_title,
                        numberOfApps,
                        numberOfApps
                    )
                )
            }
        }
        if (installAutomatically) {
            notificationBuilder.setContentText(context.getString(R.string.automatically_install_updates_notification_text))
            if (unmeteredNetworkOnly && !isConnectedToUnmeteredNetwork) {
                notificationBuilder.setSubText(
                    context
                        .getString(R.string.updates_notification_unmetered_network_warning)
                )
            }
        } else {
            notificationBuilder.setContentText(context.getString(R.string.manually_install_updates_notification_text))
        }
        notificationBuilder.setContentIntent(getClickIntent(context))
        notificationBuilder.setAutoCancel(true)
        return notificationBuilder.build()
    }

    private fun createNotificationBuilder(context: Context): NotificationCompat.Builder {
        val notificationBuilder =
            NotificationCompat.Builder(context, UPDATES_NOTIFICATION_CHANNEL_ID)

        notificationBuilder.setSmallIcon(R.drawable.ic_app_updated_on)
        notificationBuilder.priority = NotificationCompat.PRIORITY_DEFAULT
        return notificationBuilder
    }

    private fun getClickIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(UPDATES_NOTIFICATION_CLICK_EXTRA, true)
        }
        return PendingIntent.getActivity(context, 0, intent, 0)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(
                UPDATES_NOTIFICATION_CHANNEL_ID,
                UPDATES_NOTIFICATION_CHANNEL_TITLE,
                importance
            )
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNotification(
        context: Context,
        numberOfApps: Int,
        installAutomatically: Boolean,
        unmeteredNetworkOnly: Boolean,
        isConnectedToUnmeteredNetwork: Boolean
    ) {
        with(NotificationManagerCompat.from(context)) {
            createNotificationChannel(context)
            notify(
                UPDATES_NOTIFICATION_ID,
                getNotification(
                    context,
                    numberOfApps,
                    installAutomatically,
                    unmeteredNetworkOnly,
                    isConnectedToUnmeteredNetwork
                )
            )
        }
    }

    fun showNotification(
        context: Context,
        title: String,
        message: String,
    ) {
        with(NotificationManagerCompat.from(context)) {
            createNotificationChannel(context)
            notify(
                UPDATES_NOTIFICATION_ID,
                getNotification(
                    context,
                    title,
                    message
                )
            )
        }
    }

    fun getNotification(context: Context, title: String, message: String): Notification {
        val notificationBuilder = createNotificationBuilder(context)
        notificationBuilder.setContentTitle(title)
        notificationBuilder.setContentText(message)
        return notificationBuilder.build()
    }

    fun cancelNotification(context: Context) {
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(UPDATES_NOTIFICATION_ID)
    }
}
