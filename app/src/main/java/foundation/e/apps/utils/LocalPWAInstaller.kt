/*
 * Copyright (C) 2025 e Foundation
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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.R
import foundation.e.apps.data.enums.Source
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.install.models.AppInstall
import foundation.e.apps.install.pkg.PwaManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import androidx.core.content.edit
import foundation.e.apps.install.pkg.PwaManager.Companion.PWA_PLAYER


@AndroidEntryPoint
class LocalPWAInstaller : Service() {
    @Inject
    lateinit var pwaManager: PwaManager

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.pwa),
                NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure the service is in the foreground
        // It is required to install PWA
        startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

        CoroutineScope(Dispatchers.IO).launch {
            // Add a short delay to allow foreground state to settle
            delay(DELAY_BEFORE_INSTALL_MS)
            installPWA()
        }

        return START_NOT_STICKY
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.pwa))
        .setContentText(getString(R.string.installing))
        .setSmallIcon(R.drawable.app_lounge_notification_icon)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private suspend fun installPWA() {
        val preferences = getSharedPreferences(PREFERENCES_FILE_NAME, Context.MODE_PRIVATE)

        // Pwa info
        val pwaUrl = "https://accounts.murena.io/auth"
        val pwaName = "Workspace"

        val localPwa = AppInstall(
            id = UUID.randomUUID().toString(),
            source = Source.LOCAL_PWA,
            name = pwaName,
            downloadURLList = mutableListOf(pwaUrl),
            iconImageUrl = (R.drawable.murena_pwa_logo).toString()
        )

        val wasInstalledBefore = preferences.getBoolean(pwaUrl, false)
        if (getPwaStatus(localPwa) != Status.INSTALLED && !wasInstalledBefore) {
            pwaManager.installPWAApp(localPwa)
            Timber.d("Installed PWA: ${localPwa.name}")

            // Mark as installed
            preferences.edit {
                putBoolean(pwaUrl, true)
            }
        } else {
            Timber.d("PWA already installed: ${localPwa.name}")
        }

        stopSelf()
    }

    private fun getPwaStatus(app: AppInstall): Status {
        val urlToCheck = app.downloadURLList.firstOrNull() ?: return Status.UNAVAILABLE

        val isInstalled = contentResolver.query(
            PWA_PLAYER.toUri(),
            arrayOf("url"),
            null, null, null
        )?.use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor else null }
                .any { it.getString(it.getColumnIndexOrThrow("url")) == urlToCheck }
        } ?: false

        return if (isInstalled) Status.INSTALLED else Status.UNAVAILABLE
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "PWA_INSTALLATION_CHANNEL"
        private const val PREFERENCES_FILE_NAME = "local_pwa_list"
        private const val NOTIFICATION_ID = 1
        private const val DELAY_BEFORE_INSTALL_MS = 1000L
    }
}
