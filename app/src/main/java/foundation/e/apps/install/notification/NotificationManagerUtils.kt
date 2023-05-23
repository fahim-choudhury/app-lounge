/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2021  E FOUNDATION
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

import android.content.Context
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.R
import foundation.e.apps.data.prefrences.PreferenceManagerModule
import javax.inject.Inject

class NotificationManagerUtils @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceManagerModule: PreferenceManagerModule
) {

    fun showDownloadNotification(title: String): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, NotificationManagerModule.DOWNLOADS)
            .setSmallIcon(R.drawable.app_lounge_notification_icon)
            .setContentTitle(title)
            .setProgress(0, 0, true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    }

    fun showUpdateNotification(updateSize: Int): NotificationCompat.Builder {
        val contentText = if (preferenceManagerModule.autoUpdatePreferred()) {
            context.getString(R.string.auto_updates_notification)
        } else {
            context.getString(R.string.manual_updates_notification)
        }
        return NotificationCompat.Builder(context, NotificationManagerModule.UPDATES)
            .setSmallIcon(R.drawable.app_lounge_notification_icon)
            .setContentTitle(
                context.resources.getQuantityString(
                    R.plurals.updates_notification_title,
                    updateSize,
                    updateSize
                )
            )
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    }
}
