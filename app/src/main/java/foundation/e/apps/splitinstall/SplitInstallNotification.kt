/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2021-2022  E FOUNDATION
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

package foundation.e.apps.splitinstall

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import foundation.e.apps.R

object SplitInstallNotification {

    private const val INSTALL_MODULE_NOTIFICATION_ID = 5555
    private const val INSTALL_PENDING_NOTIFICATION_ID = 6555
    private const val MODULE_INSTALLED_NOTIFICATION_ID = 7555

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(
                SplitInstallService.NOTIFICATION_CHANNEL_ID,
                SplitInstallService.NOTIFICATION_CHANNEL_NAME,
                importance
            )

            channel.description = "Channel for on-demand delivery module"

            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showInstallModule(context: Context, packageName: String, modulePath: String) {

        val installIntent = Intent(context, SplitInstallBroadcastReceiver::class.java).apply {
            action = SplitInstallBroadcastReceiver.ACTION_INSTALL_MODULE
            putExtra(SplitInstallBroadcastReceiver.EXTRA_INSTALL_PACKAGE_NAME, packageName)
            putExtra(SplitInstallBroadcastReceiver.EXTRA_INSTALL_MODULE_NAME, modulePath)
        }

        val installPendingIntent = PendingIntent.getBroadcast(
            context,
            INSTALL_MODULE_NOTIFICATION_ID,
            installIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(
            context,
            SplitInstallService.NOTIFICATION_CHANNEL_ID
        )

        builder.setSmallIcon(R.drawable.app_lounge_notification_icon)
            .setContentText(context.getString(
                R.string.split_install_notification_text,
                packageName)
            )
            .setContentTitle(context.getString(R.string.split_install_notification_title))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    context.getString(
                        R.string.split_install_notification_big_text,
                        packageName,
                        extractModuleName(modulePath))
                )
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.app_lounge_notification_icon,
                context.getString(R.string.split_install_action_button),
                installPendingIntent
            )

        with(NotificationManagerCompat.from(context)) {
            notify(INSTALL_MODULE_NOTIFICATION_ID, builder.build())
        }
    }

    fun showPendingModule(context: Context, packageName: String) {
        val builder =
            NotificationCompat.Builder(context, SplitInstallService.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.app_lounge_notification_icon)
                .setAutoCancel(true)
                .setContentTitle(context.getString(R.string.split_install_notification_title))
                .setContentText(
                    context.getString(R.string.split_install_pending_install_text, packageName)
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)

        removeNotification(context, INSTALL_MODULE_NOTIFICATION_ID)
        with(NotificationManagerCompat.from(context)) {
            notify(INSTALL_PENDING_NOTIFICATION_ID, builder.build())
        }
    }

    fun showModuleInstalled(context: Context, packageName: String, status: Int) {
        val builder =
            NotificationCompat.Builder(context, SplitInstallService.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.app_lounge_notification_icon)
                .setAutoCancel(true)
                .setContentTitle(context.getString(R.string.split_install_notification_title))
                .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (status == 0) {
            builder.setContentText(context.getString(R.string.split_install_success_text))
            builder.setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    context.getString(
                        R.string.split_install_success_big_text,
                        packageName,
                        packageName
                    )
                )
            )
        } else {
            builder.setContentText(context.getString(R.string.split_install_failure_text))
            builder.setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    context.getString(
                        R.string.split_install_failure_big_text,
                        packageName
                    )
                )
            )
        }

        removeNotification(context, INSTALL_PENDING_NOTIFICATION_ID)
        with(NotificationManagerCompat.from(context)) {
            notify(MODULE_INSTALLED_NOTIFICATION_ID, builder.build())
        }

    }

    private fun removeNotification(context: Context, id: Int) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.cancel(id)
    }

    private fun extractModuleName(modulePath: String): String {
        return modulePath.substringAfter("split.").substringBefore(".apk")
    }
}


